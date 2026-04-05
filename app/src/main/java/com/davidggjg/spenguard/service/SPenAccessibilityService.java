package com.davidggjg.spenguard.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * AccessibilityService שמזהה כשתפריט Air Command נפתח.
 * על S25 Ultra — Air Command נפתח אוטומטית כשהעט נשלף מה-slot.
 * זו הדרך היחידה לזהות שליפה ב-S25 Ultra ללא BLE.
 *
 * המשתמש צריך להפעיל את השירות ב:
 * הגדרות → נגישות → SPenGuard → הפעל
 */
public class SPenAccessibilityService extends AccessibilityService {

    private static final String TAG = "SPenGuard";
    private static final String AIR_COMMAND_PKG = "com.samsung.android.service.aircommand";

    private long lastTriggerTime = 0;
    private static final long COOLDOWN_MS = 3000; // מניעת double-trigger

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String pkg = event.getPackageName() != null
                ? event.getPackageName().toString() : "";

        int type = event.getEventType();

        // זיהוי: חלון Air Command נפתח
        if (AIR_COMMAND_PKG.equals(pkg)
                && type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            long now = System.currentTimeMillis();
            if (now - lastTriggerTime < COOLDOWN_MS) {
                Log.d(TAG, "Air Command opened but in cooldown, skipping");
                return;
            }

            lastTriggerTime = now;
            Log.i(TAG, "Air Command opened = S Pen removed! Triggering guard...");
            triggerGuard();
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

    @Override
    public void onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "SPenAccessibilityService connected");
    }
}
