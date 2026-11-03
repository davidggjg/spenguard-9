package com.davidggjg.spenguard.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.davidggjg.spenguard.util.ServiceUtils;

import java.util.List;

/**
 * Detects S Pen removal by watching for Samsung's Air Command menu, which
 * opens when the pen is detached. This path also works when the broadcast is
 * not delivered (e.g. screen off on some firmware versions).
 */
public class SPenAccessibilityService extends AccessibilityService {

    private static final String TAG = "SPENguard";
    private static final String AIR_COMMAND_PKG = "com.samsung.android.service.aircommand";
    private static final long COOLDOWN_MS = 3000;
    private static final long CLOSE_CHECK_DELAY_MS = 500;

    private long lastTriggerTime = 0;
    private boolean airCommandWasOpen = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        try {
            handleEvent(event);
        } catch (Exception e) {
            // An exception here would tear down the accessibility service and
            // silently disable the guard.
            Log.e(TAG, "onAccessibilityEvent failed: " + e);
        }
    }

    private void handleEvent(AccessibilityEvent event) {
        String pkg = event.getPackageName() != null
                ? event.getPackageName().toString() : "";
        int type = event.getEventType();

        // Air Command opened => the pen was pulled out.
        if (AIR_COMMAND_PKG.equals(pkg)
                && type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            long now = System.currentTimeMillis();
            if (now - lastTriggerTime < COOLDOWN_MS) {
                return;
            }
            lastTriggerTime = now;
            airCommandWasOpen = true;

            Log.i(TAG, "Air Command opened -> S Pen removed");

            wakeScreen();
            triggerGuard();
        }

        // Air Command closed => the pen is back.
        if (airCommandWasOpen && type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            handler.postDelayed(() -> {
                try {
                    if (!isAirCommandOpen()) {
                        airCommandWasOpen = false;
                        Log.i(TAG, "Air Command closed -> pen returned -> stopping guard");
                        stopGuard();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "close check failed: " + e);
                }
            }, CLOSE_CHECK_DELAY_MS);
        }
    }

    private boolean isAirCommandOpen() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows == null) {
                return false;
            }
            for (AccessibilityWindowInfo w : windows) {
                if (w == null) {
                    continue;
                }
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null) {
                    continue;
                }
                try {
                    CharSequence pkg = root.getPackageName();
                    if (pkg != null && AIR_COMMAND_PKG.contentEquals(pkg)) {
                        return true;
                    }
                } finally {
                    root.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "isAirCommandOpen: " + e);
        }
        return false;
    }

    private void wakeScreen() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK
                                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                                | PowerManager.ON_AFTER_RELEASE,
                        "SPENguard:ScreenWake");
                // Timed acquire: released automatically, so it can never leak.
                wl.acquire(3000);
            }
        } catch (Exception e) {
            Log.e(TAG, "wakeScreen: " + e);
        }
    }

    private void triggerGuard() {
        Intent svc = new Intent(this, SPenGuardService.class);
        svc.setAction(SPenGuardService.ACTION_SPEN_REMOVED);
        ServiceUtils.startForegroundServiceSafe(this, svc);
    }

    private void stopGuard() {
        if (!SPenGuardService.isActive()) {
            return;
        }
        Intent svc = new Intent(this, SPenGuardService.class);
        svc.setAction(SPenGuardService.ACTION_STOP);
        ServiceUtils.startServiceSafe(this, svc);
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt.
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "SPenAccessibilityService connected and ready");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        handler.removeCallbacksAndMessages(null);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
