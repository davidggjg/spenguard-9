package com.davidggjg.spenguard.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.davidggjg.spenguard.service.SPenGuardService;

public class StopAlarmActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        setContentView(R.layout.activity_stop_alarm);

        findViewById(R.id.btnStop).setOnClickListener(v -> {
            Intent stop = new Intent(this, SPenGuardService.class);
            stop.setAction(SPenGuardService.ACTION_STOP);
            startService(stop);
            finish();
        });
    }
}
