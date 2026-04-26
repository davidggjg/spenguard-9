package com.davidggjg.spenguard.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.davidggjg.spenguard.service.SPenGuardService;
import com.davidggjg.spenguard.service.WatchdogService;

public class SPenReceiver extends BroadcastReceiver {

    private static final String TAG = "SPenGuard";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        Log.i(TAG, "SPenReceiver: " + action);

        switch (action) {
            case "com.samsung.pen.INSERT":
                // זה ה-broadcast הרשמי של חיישן Hall — עובד גם עם מסך כבוי!
                boolean penInserted = intent.getBooleanExtra("penInsert", true);
                if (!penInserted) {
                    Log.i(TAG, "S Pen REMOVED (Hall sensor broadcast)");
                    triggerGuard(context);
                } else {
                    Log.i(TAG, "S Pen INSERTED (Hall sensor broadcast)");
                    stopGuard(context);
                }
                break;

            case "com.samsung.android.app.spen.SPEN_OUT_STATE_CHANGED":
                int state = intent.getIntExtra("state", -1);
                Log.i(TAG, "SPEN_OUT_STATE_CHANGED state=" + state);
                if (state == 1) {
                    triggerGuard(context);
                } else if (state == 0) {
                    stopGuard(context);
                }
                break;

            case "com.samsung.android.app.spen.remote.SPEN_DETACHED":
            case "com.samsung.android.cocktail.v2.action.SPEN_DETACHED":
                Log.i(TAG, "S Pen detached broadcast");
                triggerGuard(context);
                break;

            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":
                Log.i(TAG, "Boot completed — starting WatchdogService");
                startWatchdog(context);
                break;
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(w);
        } else {
            context.startService(w);
        }
    }
}
