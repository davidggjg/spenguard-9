package com.davidggjg.spenguard.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

public class SPenAccessibilityService extends AccessibilityService {

    private static final String TAG = "SPenGuard";
    private static final String AIR_COMMAND_PKG = "com.samsung.android.service.aircommand";
    private static final long COOLDOWN_MS = 3000;

    private long lastTriggerTime = 0;
    private boolean airCommandOpen = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String pkg = event.getPackageName() != null
                ? event.getPackageName().toString() : "";
        int type = event.getEventType();

        // Air Command נפתח = העט נשלף
        if (AIR_COMMAND_PKG.equals(pkg)
                && type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            long now = System.currentTimeMillis();
            if (now - lastTriggerTime < COOLDOWN_MS) return;
            lastTriggerTime = now;
            airCommandOpen = true;
            Log.i(TAG, "Air Command opened = S Pen removed!");
            triggerGuard();
        }

        // בדיקה אם Air Command נסגר = העט חזר
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED && airCommandOpen) {
            boolean stillOpen = false;
            try {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo w : windows) {
                        CharSequence title = w.getTitle();
                        if (title != null && title.toString().toLowerCase()
                                .contains("air")) {
                            stillOpen = true;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "getWindows: " + e.getMessage());
            }

            if (!stillOpen) {
                airCommandOpen = false;
                Log.i(TAG, "Air Command closed = pen back = stopping guard");
                stopService(new Intent(this, SPenGuardService.class));
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
