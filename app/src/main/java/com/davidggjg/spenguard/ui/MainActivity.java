package com.davidggjg.spenguard.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.davidggjg.spenguard.R;
import com.davidggjg.spenguard.service.WatchdogService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ = 100;

    private Switch   guardSwitch;
    private TextView statusIcon;
    private TextView statusText;
    private Button   startBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        guardSwitch = findViewById(R.id.guardSwitch);
        statusIcon  = findViewById(R.id.statusIcon);
        statusText  = findViewById(R.id.statusText);
        startBtn    = findViewById(R.id.startButton);

        startBtn.setOnClickListener(v -> {
            if (hasCameraPermission()) startGuard();
            else requestPerms();
        });

        guardSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                if (hasCameraPermission()) startGuard();
                else { guardSwitch.setChecked(false); requestPerms(); }
            } else {
                stopGuard();
            }
        });

        if (hasCameraPermission()) {
            startGuard();
            guardSwitch.setChecked(true);
        } else {
            setStatus(false);
        }
    }

    private void startGuard() {
        Intent i = new Intent(this, WatchdogService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        setStatus(true);
        Toast.makeText(this, "🛡️ SPenGuard מופעל", Toast.LENGTH_SHORT).show();
    }

    private void stopGuard() {
        stopService(new Intent(this, WatchdogService.class));
        setStatus(false);
        Toast.makeText(this, "SPenGuard כבוי", Toast.LENGTH_SHORT).show();
    }

    private void setStatus(boolean on) {
        if (on) {
            statusIcon.setText("🛡️");
            statusText.setText("הגנה פעילה!\nשליפת S Pen → צילום + אזעקה 5 שניות");
            startBtn.setText("הגנה פעילה ✓");
            startBtn.setEnabled(false);
        } else {
            statusIcon.setText("⚠️");
            statusText.setText("לחץ כדי להפעיל את ההגנה");
            startBtn.setText("הפעל הגנה");
            startBtn.setEnabled(true);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPerms() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
            list.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        ActivityCompat.requestPermissions(this, list.toArray(new String[0]), REQ);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ && hasCameraPermission()) {
            startGuard();
            guardSwitch.setChecked(true);
        } else {
            Toast.makeText(this, "נדרשת הרשאת מצלמה", Toast.LENGTH_LONG).show();
        }
    }
}
