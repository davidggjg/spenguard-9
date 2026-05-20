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
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.davidggjg.spenguard.receiver.SPenReceiver;

public class WatchdogService extends Service {

    private static final String TAG = "SPenGuard";
    private static final String CHANNEL_ID = "spenguard_watchdog";
    private static final int NOTIF_ID = 1002;

    public static final String ACTION_START = "com.davidggjg.spenguard.WATCHDOG_START";
    public static final String ACTION_STOP  = "com.davidggjg.spenguard.WATCHDOG_STOP";

    private SPenReceiver sPenReceiver;
    private BroadcastReceiver screenReceiver;
    private Handler mainHandler;
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            Log.i(TAG, "WatchdogService: STOP received");
            shutDown();
            return START_NOT_STICKY;
        }

        // START או null — הפעל רק אם לא כבר פועל
        if (!isRunning) {
            isRunning = true;
            startForeground(NOTIF_ID, buildNotification());
            registerSPenReceiver();
            registerScreenReceiver();
            Log.i(TAG, "WatchdogService started");
        }

        // START_NOT_STICKY — לא יתחיל מחדש אוטומטית
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        shutDown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void shutDown() {
        isRunning = false;
        unregisterSafe(sPenReceiver);
        unregisterSafe(screenReceiver);
        sPenReceiver = null;
        screenReceiver = null;
        stopForeground(true);
        stopSelf();
    }

    // ── S Pen receiver ────────────────────────────────────────────────────

    private void registerSPenReceiver() {
        if (sPenReceiver != null) return;
        sPenReceiver = new SPenReceiver();
        IntentFilter f = new IntentFilter();
        f.addAction("com.samsung.pen.INSERT");
        f.addAction("com.samsung.android.app.spen.remote.SPEN_DETACHED");
        f.addAction("com.samsung.android.cocktail.v2.action.SPEN_DETACHED");
        f.addAction("com.samsung.android.app.spen.SPEN_OUT_STATE_CHANGED");
        f.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiverSafe(sPenReceiver, f);
    }

    // ── Screen receiver ───────────────────────────────────────────────────

    private void registerScreenReceiver() {
        if (screenReceiver != null) return;
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                // רק לצורך לוג — הזיהוי נעשה דרך com.samsung.pen.INSERT
                String action = intent.getAction();
                Log.d(TAG, "Screen event: " + action);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiverSafe(screenReceiver, f);
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
