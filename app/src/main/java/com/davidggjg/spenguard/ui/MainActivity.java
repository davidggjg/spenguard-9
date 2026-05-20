package com.davidggjg.spenguard.ui;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
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

    private static final int REQ_PERM = 100;

    private TextView statusIcon;
    private TextView statusText;
    private Button actionBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusIcon = findViewById(R.id.statusIcon);
        statusText = findViewById(R.id.statusText);
        actionBtn  = findViewById(R.id.startButton);

        actionBtn.setOnClickListener(v -> handleButtonClick());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void handleButtonClick() {
        if (!hasCameraPermission()) {
            requestPerms();
        } else if (!isAccessibilityEnabled()) {
            // פתח ישירות להגדרות נגישות
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(this,
                "מצא SPenGuard ← לחץ עליו ← אפשר הגדרות מוגבלות ← הפעל",
                Toast.LENGTH_LONG).show();
        } else {
            // הכל תקין — הפעל
            startGuard();
        }
    }

    private void updateUI() {
        if (!hasCameraPermission()) {
            statusIcon.setText("!");
            statusText.setText("נדרשת הרשאת מצלמה");
            actionBtn.setText("תן הרשאה");
            actionBtn.setEnabled(true);

        } else if (!isAccessibilityEnabled()) {
            statusIcon.setText("!");
            statusText.setText(
                "נדרשת הרשאת נגישות\n\n" +
                "1. לחץ על הכפתור למטה\n" +
                "2. מצא SPenGuard ברשימה\n" +
                "3. לחץ 3 נקודות ← אפשר הגדרות מוגבלות\n" +
                "4. חזור ולחץ על SPenGuard ← הפעל"
            );
            actionBtn.setText("פתח הגדרות נגישות");
            actionBtn.setEnabled(true);

        } else {
            // הכל עובד — הפעל אוטומטית
            startGuard();
            statusIcon.setText("ON");
            statusText.setText(
                "הגנה פעילה!\n\n" +
                "מצלם עם המצלמה הקדמית\n" +
                "מצפצף 5 שניות מעל שקט\n" +
                "עובד גם עם מסך כבוי\n" +
                "שומר תמונה לגלריה אוטומטית\n" +
                "מתחיל אוטומטי אחרי ריסטארט"
            );
            actionBtn.setEnabled(false);
            actionBtn.setText("פעיל");
        }
    }

    private void startGuard() {
        Intent i = new Intent(this, WatchdogService.class);
        i.setAction(WatchdogService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am =
            (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null) return false;

        List<AccessibilityServiceInfo> list =
            am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);

        for (AccessibilityServiceInfo info : list) {
            ServiceInfo si = info.getResolveInfo().serviceInfo;
            if (getPackageName().equals(si.packageName)) {
                return true;
            }
        }
        return false;
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
        ActivityCompat.requestPermissions(this,
                list.toArray(new String[0]), REQ_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int code,
            @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        updateUI();
    }
}
