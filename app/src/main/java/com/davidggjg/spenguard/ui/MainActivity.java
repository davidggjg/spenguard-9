package com.davidggjg.spenguard.ui;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.davidggjg.spenguard.R;
import com.davidggjg.spenguard.receiver.SPenDeviceAdminReceiver;
import com.davidggjg.spenguard.service.WatchdogService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERM = 100;
    private static final int REQ_DEVICE_ADMIN = 200;

    private SwitchCompat guardSwitch;
    private TextView statusIcon;
    private TextView statusText;
    private Button startBtn;

    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        guardSwitch = findViewById(R.id.guardSwitch);
        statusIcon  = findViewById(R.id.statusIcon);
        statusText  = findViewById(R.id.statusText);
        startBtn    = findViewById(R.id.startButton);

        dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, SPenDeviceAdminReceiver.class);

        startBtn.setOnClickListener(v -> activate());
        guardSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) activate();
            else deactivate();
        });

        if (hasCameraPermission() && isDeviceAdminActive()) {
            startGuard();
            guardSwitch.setChecked(true);
        } else {
            setStatus(false);
        }
    }

    private void activate() {
        if (!hasCameraPermission()) {
            requestPerms();
            guardSwitch.setChecked(false);
        } else if (!isDeviceAdminActive()) {
            requestDeviceAdmin();
            guardSwitch.setChecked(false);
        } else {
            startGuard();
            guardSwitch.setChecked(true);
        }
    }

    private void deactivate() {
        stopGuard();
    }

    private void startGuard() {
        Intent i = new Intent(this, WatchdogService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        setStatus(true);
        Toast.makeText(this, "SPenGuard מופעל", Toast.LENGTH_SHORT).show();
    }

    private void stopGuard() {
        stopService(new Intent(this, WatchdogService.class));
        setStatus(false);
        Toast.makeText(this, "SPenGuard כבוי", Toast.LENGTH_SHORT).show();
    }

    private void setStatus(boolean on) {
        if (on) {
            statusIcon.setText("ON");
            statusText.setText("הגנה פעילה!\nשליפת S Pen = צילום + אזעקה");
            startBtn.setText("פעיל");
            startBtn.setEnabled(false);
        } else {
            statusIcon.setText("!");
            statusText.setText("לחץ להפעלה");
            startBtn.setText("הפעל הגנה");
            startBtn.setEnabled(true);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isDeviceAdminActive() {
        return dpm != null && dpm.isAdminActive(adminComponent);
    }

    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "SPenGuard צריך הרשאת Device Admin כדי לעבוד עם מסך כבוי");
        startActivityForResult(intent, REQ_DEVICE_ADMIN);
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
        ActivityCompat.requestPermissions(this, list.toArray(new String[0]), REQ_PERM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DEVICE_ADMIN) {
            if (isDeviceAdminActive()) {
                startGuard();
                guardSwitch.setChecked(true);
            } else {
                Toast.makeText(this, "נדרשת הרשאת Device Admin", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_PERM && hasCameraPermission()) {
            activate();
        } else {
            Toast.makeText(this, "נדרשת הרשאת מצלמה", Toast.LENGTH_LONG).show();
        }
    }
}
