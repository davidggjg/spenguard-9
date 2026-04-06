package com.davidggjg.spenguard.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.davidggjg.spenguard.receiver.SPenReceiver;

public class WatchdogService extends Service {

    private static final String TAG = "SPenGuard";
    private static final String CHANNEL_ID = "spenguard_watchdog";
    private static final int NOTIF_ID = 1002;

    // polling כל שנייה כשהמסך כבוי
    private static final int POLL_INTERVAL_MS = 1000;

    private SPenReceiver sPenReceiver;
    private BroadcastReceiver screenReceiver;
    private Handler pollHandler;
    private boolean isPolling = false;
    private boolean lastPenWasOut = false;

    @Override
    public void onCreate() {
        super.onCreate();
        pollHandler = new Handler(Looper.getMainLooper());
        createChannel();
        startForeground(NOTIF_ID, buildNotification());
        registerSPenReceiver();
        registerScreenReceiver();
        Log.i(TAG, "WatchdogService started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // אוטומטי restart אם נכבה
    }

    @Override
    public void onDestroy() {
        stopPolling();
        unregister(sPenReceiver);
        unregister(screenReceiver);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── S Pen receiver (מסך פתוח) ─────────────────────────────────────────

    private void registerSPenReceiver() {
        sPenReceiver = new SPenReceiver();
        IntentFilter f = new IntentFilter();
        f.addAction("com.samsung.android.app.cocktail.COCKTAIL_VIEW");
        f.addAction("com.samsung.android.app.spen.remote.SPEN_DETACHED");
        f.addAction("com.samsung.android.cocktail.v2.action.SPEN_DETACHED");
        f.addAction("com.samsung.android.app.spen.SPEN_OUT_STATE_CHANGED");
        f.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiverSafe(sPenReceiver, f);
        Log.i(TAG, "S Pen receiver registered");
    }

    // ── Screen receiver + polling (מסך כבוי) ──────────────────────────────

    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    Log.i(TAG, "Screen OFF → starting S Pen poll");
                    startPolling();
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    Log.i(TAG, "Screen ON → stopping poll");
                    stopPolling();
                    lastPenWasOut = false;
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        // SCREEN_OFF/ON חייבים להירשם dynamically — לא ב-manifest
        registerReceiverSafe(screenReceiver, f);
        Log.i(TAG, "Screen receiver registered");
    }

    // ── Polling — בודק כל שנייה אם העט בחוץ ─────────────────────────────

    private void startPolling() {
        if (isPolling) return;
        isPolling = true;
        pollHandler.post(pollRunnable);
    }

    private void stopPolling() {
        isPolling = false;
        pollHandler.removeCallbacks(pollRunnable);
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPolling) return;

            boolean penOut = isPenOut();

            if (penOut && !lastPenWasOut) {
                Log.i(TAG, "Pen removed while screen OFF → triggering guard!");
                lastPenWasOut = true;
                triggerGuard();
            } else if (!penOut && lastPenWasOut) {
                Log.i(TAG, "Pen returned while screen OFF → stopping guard");
                lastPenWasOut = false;
                stopGuard();
            }

            if (isPolling) {
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    /**
     * בדיקה אם ה-S Pen בחוץ.
     * על S25 Ultra — קורא את /proc/bus/input/devices ומחפש switch state.
     * גם אם זה לא עובד — עדיין מנסה דרך /sys/class.
     */
    private boolean isPenOut() {
        // שיטה 1: קרא /proc/bus/input/devices
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/bus/input/devices"));
            String line;
            boolean inSPenDevice = false;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("N: Name=")) {
                    String name = line.toLowerCase();
                    inSPenDevice = name.contains("spen") || name.contains("e-pen")
                            || name.contains("hall") || name.contains("gpio-keys")
                            || name.contains("sec_e-pen");
                }
                // B: SW= מכיל את state של ה-switch
                // bit 0 = SW_LID (lid/slot open=1 closed=0)
                if (inSPenDevice && line.startsWith("B: SW=")) {
                    String hexVal = line.replace("B: SW=", "").trim();
                    try {
                        long val = Long.parseLong(hexVal, 16);
                        br.close();
                        // bit 0 = 1 → מתג פתוח = עט בחוץ
                        return (val & 0x1L) != 0;
                    } catch (NumberFormatException ignored) {}
                }
            }
            br.close();
        } catch (Exception e) {
            Log.d(TAG, "proc read: " + e.getMessage());
        }

        // שיטה 2: בדוק /sys/class/sec/tsp/input/spen_insert
        String[] sysPaths = {
            "/sys/class/sec/tsp/input/spen_insert",
            "/sys/devices/virtual/input/spen/spen_insert",
            "/sys/class/input/spen/spen_insert"
        };
        for (String path : sysPaths) {
            try {
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.FileReader(path));
                String val = br.readLine();
                br.close();
                if (val != null) {
                    // 0 = inserted, 1 = removed
                    return "1".equals(val.trim());
                }
            } catch (Exception ignored) {}
        }

        return false;
    }

    private void triggerGuard() {
        // מעיר מסך
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK |
                        PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.ON_AFTER_RELEASE,
                        "SPenGuard:Wake");
                wl.acquire(5000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Wake: " + e.getMessage());
        }

        Intent svc = new Intent(this, SPenGuardService.class);
        svc.setAction(SPenGuardService.ACTION_SPEN_REMOVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }

    private void stopGuard() {
        Intent svc = new Intent(this, SPenGuardService.class);
        svc.setAction(SPenGuardService.ACTION_STOP);
        startService(svc);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void registerReceiverSafe(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(r, f);
        }
    }

    private void unregister(BroadcastReceiver r) {
        if (r != null) {
            try { unregisterReceiver(r); } catch (Exception ignored) {}
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "SPenGuard פעיל", NotificationManager.IMPORTANCE_MIN);
        ch.setSound(null, null);
        ch.setShowBadge(false);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SPenGuard פעיל")
                .setContentText("מאזין לשליפת S Pen...")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setOngoing(true)
                .build();
    }
}
