package com.davidggjg.spenguard.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.davidggjg.spenguard.service.SPenGuardService;

public class SPenDeviceAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "SPenGuard";

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
        // לא בשימוש — רק כדי שה-Device Admin יהיה פעיל
    }
}
