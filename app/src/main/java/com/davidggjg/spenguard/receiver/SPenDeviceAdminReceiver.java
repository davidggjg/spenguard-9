package com.davidggjg.spenguard.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Device-admin receiver. SPENguard only uses the force-lock / watch-login
 * policies declared in res/xml/device_admin.xml so that it keeps running
 * while the screen is off; no management actions are performed here.
 */
public class SPenDeviceAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "SPENguard";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.i(TAG, "Device Admin enabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.i(TAG, "Device Admin disabled");
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        // Intentionally empty - declared only so the policy stays active.
    }
}
