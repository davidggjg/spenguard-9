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

    private SPenReceiver sPenReceiver;
    private BroadcastReceiver screenReceiver;
    private Handler mainHandler;

    private boolean wasScreenOff = false;
    private boolean guardTriggeredFromScreenOff = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        createChannel();
        startForeground(NOTIF_ID, buildNotification());
        registerSPenReceiver();
        registerScreenReceiver();
        Log.i(TAG, "WatchdogService started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterSafe(sPenReceiver);
        unregisterSafe(screenReceiver);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── S Pen receiver (מסך פתוח — broadcast רגיל) ───────────────────────

    private void registerSPenReceiver() {
        sPenReceiver = new SPenReceiver();
        IntentFilter f = new IntentFilter();
        f.addAction("com.samsung.android.app.cocktail.COCKTAIL_VIEW");
        f.addAction("com.samsung.android.app.spen.remote.SPEN_DETACHED");
        f.addAction("com.samsung.android.cocktail.v2.action.SPEN_DETACHED");
        f.addAction("com.samsung.android.app.spen.SPEN_OUT_STATE_CHANGED");
        f.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiverSafe(sPenReceiver, f);
    }

    // ── Screen receiver ───────────────────────────────────────────────────
    // הלוגיקה:
    // 1. מסך כובה → שומרים wasScreenOff=true
    // 2. מסך נדלק → אם wasScreenOff=true, זה אומר שמשהו הדליק אותו
    //    → בודקים אם Air Command פתוח (= עט נשלף)
    //    → אם כן → מפעילים Guard

    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();

                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    wasScreenOff = true;
                    guardTriggeredFromScreenOff = false;
                    Log.i(TAG, "Screen OFF — watching for S Pen removal");

                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    if (wasScreenOff && !guardTriggeredFromScreenOff) {
                        Log.i(TAG, "Screen turned ON from OFF state — checking S Pen");
                        // מחכים 300ms כדי לתת ל-Air Command להיפתח
                        mainHandler.postDelayed(() -> {
                            if (isAirCommandOpen()) {
                                Log.i(TAG, "Air Command open after screen-off wake → S Pen removed!");
                                guardTriggeredFromScreenOff = true;
                                triggerGuard();
                            } else {
                                Log.i(TAG, "Screen turned on normally — no S Pen removal");
                            }
                            wasScreenOff = false;
                        }, 300);
                    }
                }
            }
        };

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiverSafe(screenReceiver, f);
    }

    // ── בדיקת Air Command ─────────────────────────────────────────────────

    private boolean isAirCommandOpen() {
        // בדיקה דרך ActivityManager — האם Air Command פועל בחזית
        android.app.ActivityManager am =
            (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;

        try {
            for (android.app.ActivityManager.RunningAppProcessInfo info
                    : am.getRunningAppProcesses()) {
                if (info.processName != null &&
                    info.processName.contains("aircommand") &&
                    info.importance ==
                        android.app.ActivityManager.RunningAppProcessInfo
                            .IMPORTANCE_FOREGROUND) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "isAirCommandOpen: " + e.getMessage());
        }

        // גיבוי — בדיקה דרך UsageStatsManager
        try {
            android.app.usage.UsageStatsManager usm =
                (android.app.usage.UsageStatsManager)
                    getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm != null) {
                long now = System.currentTimeMillis();
                java.util.Map<String,
                    android.app.usage.UsageStats> stats =
                        usm.queryAndAggregateUsageStats(now - 2000, now);
                if (stats != null) {
                    android.app.usage.UsageStats airCmd =
                        stats.get("com.samsung.android.service.aircommand");
                    if (airCmd != null &&
                        airCmd.getLastTimeUsed() > now - 1500) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "UsageStats: " + e.getMessage());
        }

        return false;
    }

    // ── Guard control ─────────────────────────────────────────────────────

    private void triggerGuard() {
        Intent svc = new Intent(this, SPenGuardService.class);
        svc.setAction(SPenGuardService.ACTION_SPEN_REMOVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }

    public void stopGuard() {
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

    private void unregisterSafe(BroadcastReceiver r) {
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
