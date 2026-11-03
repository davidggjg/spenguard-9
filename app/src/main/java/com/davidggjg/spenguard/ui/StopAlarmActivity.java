package com.davidggjg.spenguard.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.davidggjg.spenguard.R;
import com.davidggjg.spenguard.service.SPenGuardService;
import com.davidggjg.spenguard.util.ServiceUtils;

/** Full-screen "stop the alarm" prompt, shown over the lock screen. */
public class StopAlarmActivity extends AppCompatActivity {

    private static final String TAG = "SPENguard";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        setContentView(R.layout.activity_stop_alarm);

        // A stray tap outside the dialog must not dismiss the screen while
        // the alarm is still sounding.
        setFinishOnTouchOutside(false);

        findViewById(R.id.btnStop).setOnClickListener(v -> {
            stopAlarm();
            finish();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void stopAlarm() {
        try {
            Intent stop = new Intent(this, SPenGuardService.class);
            stop.setAction(SPenGuardService.ACTION_STOP);
            ServiceUtils.startServiceSafe(this, stop);
        } catch (Exception e) {
            Log.e(TAG, "Could not stop the alarm service: " + e);
        }
    }
}
