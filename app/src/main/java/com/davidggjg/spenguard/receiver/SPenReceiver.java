package com.davidggjg.spenguard.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import com.davidggjg.spenguard.service.SPenGuardService;
import com.davidggjg.spenguard.service.WatchdogService;

public class SPenReceiver extends BroadcastReceiver {

    private static final String TAG = "SPenGuard";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        Log.d(TAG, "Received: " + action);

        switch (action) {

            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":
                Log.i(TAG, "Boot → starting watchdog");
                startService(context, WatchdogService.class);
                break;

            // BLE-based — Note9 to S24 Ultra
            case "com.samsung.android.app.cocktail.COCKTAIL_VIEW":
            case "com.samsung.android.app.spen.remote.SPEN_DETACHED":
            case "com.samsung.android.cocktail.v2.action.SPEN_DETACHED":
                Log.i(TAG, "S Pen removed [BLE]");
                triggerGuard(context);
                break;

            // EMF-based — S25 Ultra / S26 Ultra (no Bluetooth)
            case "com.samsung.android.app.spen.SPEN_OUT_STATE_CHANGED":
                if (intent.getBooleanExtra("spen_out", false)) {
                    Log.i(TAG, "S Pen removed [EMF]");
                    triggerGuard(context);
                }
                break;

            default:
                break;
        }
    }

    private void triggerGuard(Context context) {
        // Wake screen if off
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) {
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK |
                    PowerManager.ACQUIRE_CAUSES_WAKEUP |
                    PowerManager.ON_AFTER_RELEASE,
                    "SPenGuard:WakeLock");
            wl.acquire(10_000L);
        }

        Intent svc = new Intent(context, SPenGuardService.class);
        svc.setAction(SPenGuardService.ACTION_SPEN_REMOVED);
        startService(context, svc);
    }

    private void startService(Context context, Class<?> cls) {
        startService(context, new Intent(context, cls));
    }

    private void startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
