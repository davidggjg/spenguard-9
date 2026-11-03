package com.davidggjg.spenguard.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.davidggjg.spenguard.service.SPenGuardService;
import com.davidggjg.spenguard.service.WatchdogService;
import com.davidggjg.spenguard.util.ServiceUtils;

/**
 * Reacts to the Samsung S Pen attach/detach broadcasts and to device boot.
 *
 * <p>Registered both in the manifest and at runtime by
 * {@link WatchdogService}; everything here is therefore idempotent and must
 * not throw, because an exception in {@code onReceive} crashes the app.
 */
public class SPenReceiver extends BroadcastReceiver {

    private static final String TAG = "SPENguard";
    public static final String PREFS = "spenguard_prefs";
    public static final String KEY_ENABLED = "guard_enabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (action == null) {
            return;
        }

        Log.i(TAG, "SPenReceiver: " + action);

        try {
            handle(context, intent, action);
        } catch (Exception e) {
            // Never let a broadcast take the whole process down.
            Log.e(TAG, "SPenReceiver failed for " + action + ": " + e);
        }
    }

    private void handle(Context context, Intent intent, String action) {
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":
                if (isGuardEnabled(context)) {
                    Log.i(TAG, "Boot - guard was enabled, restarting WatchdogService");
                    startWatchdog(context);
                } else {
                    Log.i(TAG, "Boot - guard was disabled, not starting");
                }
                break;

            case "com.samsung.pen.INSERT": {
                boolean penInserted = intent.getBooleanExtra("penInsert", true);
                if (!penInserted) {
                    Log.i(TAG, "S Pen REMOVED");
                    if (isGuardEnabled(context)) {
                        triggerGuard(context);
                    } else {
                        Log.i(TAG, "Guard disabled - ignoring S Pen removal");
                    }
                } else {
                    Log.i(TAG, "S Pen INSERTED");
                    stopGuard(context);
                }
                break;
            }

            case "com.samsung.android.app.spen.SPEN_OUT_STATE_CHANGED": {
                int state = intent.getIntExtra("state", -1);
                if (state == 1 && isGuardEnabled(context)) {
                    triggerGuard(context);
                } else if (state == 0) {
                    stopGuard(context);
                }
                break;
            }

            case "com.samsung.android.app.spen.remote.SPEN_DETACHED":
            case "com.samsung.android.cocktail.v2.action.SPEN_DETACHED":
                if (isGuardEnabled(context)) {
                    triggerGuard(context);
                }
                break;

            default:
                break;
        }
    }

    private boolean isGuardEnabled(Context context) {
        try {
            SharedPreferences prefs =
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return prefs.getBoolean(KEY_ENABLED, false);
        } catch (Exception e) {
            Log.e(TAG, "Cannot read preferences: " + e);
            return false;
        }
    }

    private void triggerGuard(Context context) {
        Intent svc = new Intent(context, SPenGuardService.class);
        svc.setAction(SPenGuardService.ACTION_SPEN_REMOVED);
        ServiceUtils.startForegroundServiceSafe(context, svc);
    }

    private void stopGuard(Context context) {
        // Only talk to the service when it is actually running. Starting it
        // just to deliver a STOP would create a foreground service that never
        // calls startForeground(), which the system punishes with a crash.
        if (!SPenGuardService.isActive()) {
            return;
        }
        Intent svc = new Intent(context, SPenGuardService.class);
        svc.setAction(SPenGuardService.ACTION_STOP);
        ServiceUtils.startServiceSafe(context, svc);
    }

    private void startWatchdog(Context context) {
        Intent w = new Intent(context, WatchdogService.class);
        w.setAction(WatchdogService.ACTION_START);
        ServiceUtils.startForegroundServiceSafe(context, w);
    }
}
