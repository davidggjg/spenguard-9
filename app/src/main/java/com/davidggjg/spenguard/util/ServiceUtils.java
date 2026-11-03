package com.davidggjg.spenguard.util;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Helpers for starting services without crashing the process.
 *
 * <p>Since Android 8 a background {@code startService()} throws
 * {@link IllegalStateException}, and since Android 12 a background
 * {@code startForegroundService()} can throw
 * {@code ForegroundServiceStartNotAllowedException}. Both are thrown straight
 * into {@code BroadcastReceiver.onReceive()} / service callbacks, where they
 * kill the app. SPENguard reacts to broadcasts it does not control, so every
 * start has to be defensive.
 */
public final class ServiceUtils {

    private static final String TAG = "SPENguard";

    private ServiceUtils() {
    }

    /**
     * Starts {@code intent} as a foreground service.
     *
     * @return true if the start request was accepted by the system.
     */
    public static boolean startForegroundServiceSafe(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            return true;
        } catch (Exception e) {
            // ForegroundServiceStartNotAllowedException (API 31+),
            // IllegalStateException, SecurityException, ...
            Log.w(TAG, "startForegroundService refused: " + e);
            return false;
        }
    }

    /** Starts a plain (non-foreground) service, swallowing background-start refusals. */
    public static boolean startServiceSafe(Context context, Intent intent) {
        try {
            context.startService(intent);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "startService refused: " + e);
            return false;
        }
    }
}
