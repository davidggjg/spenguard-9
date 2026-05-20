package com.davidggjg.spenguard.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.davidggjg.spenguard.service.SPenGuardService;
import com.davidggjg.spenguard.service.WatchdogService;

public class SPenReceiver extends BroadcastReceiver {

    private static final String TAG = "SPenGuard";
    private static final String PREFS = "spenguard_prefs";
    private static final String KEY_ENABLED = "guard_enabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        Log.i(TAG, "SPenReceiver: " + action);

        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":
                // אחרי ריסטרט — הפעל רק אם היה מופעל לפני
                if (isGuardEnabled(context)) {
                    Log.i(TAG, "Boot — guard was enabled, restarting WatchdogService");
                    startWatchdog(context);
                } else {
                    Log.i(TAG, "Boot — guard was disabled, not starting");
                }
                break;

            case "com.samsung.pen.INSERT":
                boolean penInserted = intent.getBooleanExtra("penInsert", true);
                if (!penInserted) {
                    Log.i(TAG, "S Pen REMOVED");
                    if (isGuardEnabled(context)) {
                        triggerGuard(context);
                    } else {
                        Log.i(TAG, "Guard disabled — ignoring S Pen removal");
                    }
                } else {
                    Log.i(TAG, "S Pen INSERTED");
                    stopGuard(context);
                }
                break;

            case "com.samsung.android.app.spen.SPEN_OUT_STATE_CHANGED":
                int state = intent.getIntExtra("state", -1);
                if (state == 1 && isGuardEnabled(context)) {
                    triggerGuard(context);
                } else if (state == 0) {
                    stopGuard(context);
                }
                break;

            case "com.samsung.android.app.spen.remote.SPEN_DETACHED":
            case "com.samsung.android.cocktail.v2.action.SPEN_DETACHED":
                if (isGuardEnabled(context)) {
                    triggerGuard(context);
                }
                break;
        }
    }

    private boolean isGuardEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    private void triggerGuard(Context context) {
        Intent svc = new Intent(context, SPenGuardService.class);
        svc.setAction(SPenGuardService.ACTION_SPEN_REMOVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }
    }

    private void stopGuard(Context context) {
        Intent svc = new Intent(context, SPenGuardService.class);
        svc.setAction(SPenGuardService.ACTION_STOP);
        context.startService(svc);
    }

    private void startWatchdog(Context context) {
        Intent w = new Intent(context, WatchdogService.class);
        w.setAction(WatchdogService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(w);
        } else {
            context.startService(w);
        }
    }
}
