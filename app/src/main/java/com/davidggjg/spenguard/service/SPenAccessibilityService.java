package com.davidggjg.spenguard.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

public class SPenAccessibilityService extends AccessibilityService {

    private static final String TAG = "SPenGuard";
    private static final String AIR_COMMAND_PKG = "com.samsung.android.service.aircommand";
    private static final long COOLDOWN_MS = 3000;

    private long lastTriggerTime = 0;
    private boolean airCommandWasOpen = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String pkg = event.getPackageName() != null
                ? event.getPackageName().toString() : "";
        int type = event.getEventType();

        // ── Air Command נפתח = עט נשלף ───────────────────────────────────
        if (AIR_COMMAND_PKG.equals(pkg)
                && type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            long now = System.currentTimeMillis();
            if (now - lastTriggerTime < COOLDOWN_MS) return;
            lastTriggerTime = now;
            airCommandWasOpen = true;

            Log.i(TAG, "Air Command opened → S Pen removed!");

            // מעיר מסך אם כבוי
            wakeScreen();

            // מפעיל את ה-Guard מיד
            triggerGuard();
        }

        // ── בדיקה אם Air Command נסגר = עט חזר ──────────────────────────
        if (airCommandWasOpen && type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            handler.postDelayed(() -> {
                if (!isAirCommandOpen()) {
                    airCommandWasOpen = false;
                    Log.i(TAG, "Air Command closed → pen returned → stopping guard");
                    stopGuard();
                }
            }, 500);
        }
    }

    private boolean isAirCommandOpen() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows == null) return false;
            for (AccessibilityWindowInfo w : windows) {
                CharSequence pkg = null;
                // בדיקה דרך root node
                if (w.getRoot() != null) {
                    pkg = w.getRoot().getPackageName();
                }
                if (pkg != null && AIR_COMMAND_PKG.equals(pkg.toString())) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "isAirCommandOpen: " + e.getMessage());
        }
        return false;
    }

    private void wakeScreen() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK |
                        PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.ON_AFTER_RELEASE,
                        "SPenGuard:ScreenWake");
                wl.acquire(3000);
            }
        } catch (Exception e) {
            Log.e(TAG, "wakeScreen: " + e.getMessage());
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

    private void stopGuard() {
        Intent svc = new Intent(this, SPenGuardService.class);
        svc.setAction(SPenGuardService.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "SPenAccessibilityService connected and ready");
    }
}
