package com.davidggjg.spenguard.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.davidggjg.spenguard.receiver.SPenReceiver;

public class WatchdogService extends Service {

    private static final String TAG = "SPenGuard";
    private static final String CHANNEL_ID = "spenguard_watchdog";
    private static final int NOTIF_ID = 1002;

    private SPenReceiver receiver;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIF_ID, buildNotification());
        registerDynamicReceiver();
        Log.i(TAG, "WatchdogService running");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (receiver != null) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void registerDynamicReceiver() {
        receiver = new SPenReceiver();
        IntentFilter f = new IntentFilter();
        f.addAction("com.samsung.android.app.cocktail.COCKTAIL_VIEW");
        f.addAction("com.samsung.android.app.spen.remote.SPEN_DETACHED");
        f.addAction("com.samsung.android.cocktail.v2.action.SPEN_DETACHED");
        f.addAction("com.samsung.android.app.spen.SPEN_OUT_STATE_CHANGED");
        f.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, f);
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
