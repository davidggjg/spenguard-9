package com.davidggjg.spenguard.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.davidggjg.spenguard.R;
import com.davidggjg.spenguard.receiver.SPenReceiver;
import com.davidggjg.spenguard.ui.MainActivity;

/**
 * Long-running foreground service that keeps runtime-registered receivers
 * alive, so S Pen broadcasts are still delivered while the screen is off.
 */
public class WatchdogService extends Service {

    private static final String TAG = "SPENguard";
    private static final String CHANNEL_ID = "spenguard_watchdog";
    private static final int NOTIF_ID = 1002;

    public static final String ACTION_START = "com.davidggjg.spenguard.WATCHDOG_START";
    public static final String ACTION_STOP = "com.davidggjg.spenguard.WATCHDOG_STOP";

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
        // Must happen on EVERY delivery: the caller used
        // startForegroundService(), so the system expects startForeground()
        // within a few seconds even if the service is already running.
        if (!promoteToForeground()) {
            shutDown();
            return START_NOT_STICKY;
        }

        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            Log.i(TAG, "WatchdogService: STOP received");
            // An explicit stop must also disarm the guard, otherwise
            // SPenReceiver would start it again after the next reboot.
            setGuardEnabled(false);
            shutDown();
            return START_NOT_STICKY;
        }

        if (!isRunning) {
            isRunning = true;
            registerSPenReceiver();
            registerScreenReceiver();
            Log.i(TAG, "WatchdogService started");
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        shutDown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setGuardEnabled(boolean enabled) {
        try {
            SharedPreferences prefs =
                    getSharedPreferences(SPenReceiver.PREFS, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(SPenReceiver.KEY_ENABLED, enabled).apply();
        } catch (Exception e) {
            Log.e(TAG, "Cannot persist settings: " + e);
        }
    }

    private boolean promoteToForeground() {
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            return true;
        } catch (Exception e) {
            // ForegroundServiceStartNotAllowedException on Android 12+ when
            // started from the background without an exemption.
            Log.e(TAG, "WatchdogService could not go foreground: " + e);
            return false;
        }
    }

    private void shutDown() {
        isRunning = false;
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        unregisterSafe(sPenReceiver);
        unregisterSafe(screenReceiver);
        sPenReceiver = null;
        screenReceiver = null;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ── S Pen receiver ────────────────────────────────────────────────────

    private void registerSPenReceiver() {
        if (sPenReceiver != null) {
            return;
        }
        sPenReceiver = new SPenReceiver();
        IntentFilter f = new IntentFilter();
        f.addAction("com.samsung.pen.INSERT");
        f.addAction("com.samsung.android.app.spen.remote.SPEN_DETACHED");
        f.addAction("com.samsung.android.cocktail.v2.action.SPEN_DETACHED");
        f.addAction("com.samsung.android.app.spen.SPEN_OUT_STATE_CHANGED");
        f.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        // These broadcasts come from the Samsung system apps, so the receiver
        // must be exported.
        registerReceiverSafe(sPenReceiver, f, ContextCompat.RECEIVER_EXPORTED);
    }

    // ── Screen receiver ───────────────────────────────────────────────────

    private void registerScreenReceiver() {
        if (screenReceiver != null) {
            return;
        }
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                // Logging only - detection happens via the S Pen broadcasts.
                Log.d(TAG, "Screen event: " + (intent != null ? intent.getAction() : null));
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        // Protected system broadcasts: not exported.
        registerReceiverSafe(screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void registerReceiverSafe(BroadcastReceiver r, IntentFilter f, int exportFlag) {
        try {
            ContextCompat.registerReceiver(this, r, f, exportFlag);
        } catch (Exception e) {
            Log.e(TAG, "registerReceiver failed: " + e);
        }
    }

    private void unregisterSafe(BroadcastReceiver r) {
        if (r != null) {
            try {
                unregisterReceiver(r);
            } catch (Exception ignored) {
                // Was never registered.
            }
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_watchdog),
                NotificationManager.IMPORTANCE_MIN);
        ch.setSound(null, null);
        ch.setShowBadge(false);
        ch.setDescription(getString(R.string.channel_watchdog_desc));
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), flags);

        // Lets the user switch protection off without opening the app -
        // expected of any app that runs a permanent foreground service.
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, WatchdogService.class).setAction(ACTION_STOP), flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.watchdog_title))
                .setContentText(getString(R.string.watchdog_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(open)
                .addAction(R.drawable.ic_notification,
                        getString(R.string.action_stop_protection), stop)
                .setSilent(true)
                .setOngoing(true)
                .build();
    }
}
