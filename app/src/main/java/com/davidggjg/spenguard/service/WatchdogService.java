package com.davidggjg.spenguard.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.InputDevice;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.davidggjg.spenguard.receiver.SPenReceiver;

public class WatchdogService extends Service implements InputManager.InputDeviceListener {

    private static final String TAG = "SPenGuard";
    private static final String CHANNEL_ID = "spenguard_watchdog";
    private static final int NOTIF_ID = 1002;

    private SPenReceiver receiver;
    private InputManager inputManager;
    private Handler handler;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createChannel();
        startForeground(NOTIF_ID, buildNotification());

        // שיטה 1: BroadcastReceiver לintents של סמסונג
        registerDynamicReceiver();

        // שיטה 2: InputDeviceListener — עובד כשהעט יוצא מה-slot
        // כאשר העט יוצא הוא הופך ל-InputDevice, וכאשר הוא נכנס הוא נעלם
        inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        inputManager.registerInputDeviceListener(this, handler);

        // בדיקה ראשונית — אולי העט כבר בחוץ
        checkCurrentPenState();

        Log.i(TAG, "WatchdogService started — listening via BroadcastReceiver + InputDeviceListener");
    }

    // ── InputDeviceListener ───────────────────────────────────────────────

    @Override
    public void onInputDeviceAdded(int deviceId) {
        // InputDevice נוסף — בדוק אם זה S Pen
        InputDevice device = inputManager.getInputDevice(deviceId);
        if (device != null && isPenDevice(device)) {
            Log.i(TAG, "S Pen InputDevice added (pen removed from slot): " + device.getName());
            triggerGuard();
        }
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        // InputDevice נמחק — העט חזר לslot, לא צריך לעשות כלום
        Log.d(TAG, "InputDevice removed: " + deviceId);
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        // לא רלוונטי
    }

    private boolean isPenDevice(InputDevice device) {
        String name = device.getName().toLowerCase();
        // שמות ידועים של S Pen כ-InputDevice על Samsung
        return name.contains("pen")
                || name.contains("spen")
                || name.contains("stylus")
                || name.contains("wacom")
                || name.contains("sec_e-pen")
                || name.contains("mms")
                || (device.getSources() & InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS;
    }

    private void checkCurrentPenState() {
        // אם העט כבר בחוץ כשהשירות מתחיל — לא נפעיל guard (רק נלמד מה state)
        int[] ids = inputManager.getInputDeviceIds();
        for (int id : ids) {
            InputDevice d = inputManager.getInputDevice(id);
            if (d != null && isPenDevice(d)) {
                Log.i(TAG, "S Pen already outside slot on startup: " + d.getName());
            }
        }
    }

    private void triggerGuard() {
        Intent svc = new Intent(this, SPenGuardService.class);
        svc.setAction(SPenGuardService.ACTION_SPEN_REMOVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }

    // ── BroadcastReceiver (שמירה כגיבוי) ─────────────────────────────────

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

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(this);
        }
        if (receiver != null) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── Notification ──────────────────────────────────────────────────────

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
