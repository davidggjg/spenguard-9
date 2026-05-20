package com.davidggjg.spenguard.ui;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

    private TextView statusIcon;
    private TextView statusText;
    private Button actionBtn;

    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusIcon = findViewById(R.id.statusIcon);
        statusText = findViewById(R.id.statusText);
        actionBtn  = findViewById(R.id.startButton);

        dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, SPenDeviceAdminReceiver.class);

        actionBtn.setOnClickListener(v -> handleStep());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private int getCurrentStep() {
        if (!hasCameraPermission())   return 1; // הרשאת מצלמה
        if (!isDeviceAdminActive())   return 2; // מנהל מכשיר
        if (!isAccessibilityEnabled()) return 3; // נגישות
        return 4; // הכל מוכן
    }

    private void handleStep() {
        switch (getCurrentStep()) {
            case 1:
                requestPerms();
                break;
            case 2:
                requestDeviceAdmin();
                break;
            case 3:
                Intent acc = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                acc.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(acc);
                break;
            case 4:
                startGuard();
                updateUI();
                break;
        }
    }

    private void updateUI() {
        switch (getCurrentStep()) {
            case 1:
                statusIcon.setText("1");
                statusText.setText(
                    "שלב 1 מתוך 3\n\n" +
                    "נדרשת הרשאת מצלמה\n" +
                    "לחץ כדי לאשר"
                );
                actionBtn.setText("תן הרשאת מצלמה");
                actionBtn.setEnabled(true);
                break;

            case 2:
                statusIcon.setText("2");
                statusText.setText(
                    "שלב 2 מתוך 3\n\n" +
                    "נדרשת הרשאת מנהל מכשיר\n\n" +
                    "זה מאפשר לאפליקציה לפעול\n" +
                    "גם כשהמסך כבוי\n\n" +
                    "לחץ כדי לאשר"
                );
                actionBtn.setText("אשר מנהל מכשיר");
                actionBtn.setEnabled(true);
                break;

            case 3:
                statusIcon.setText("3");
                statusText.setText(
                    "שלב 3 מתוך 3 — הגדרת נגישות\n\n" +
                    "① לחץ על הכפתור למטה\n" +
                    "② מצא SPenGuard ברשימה\n" +
                    "③ אם כתוב חסום — לחץ עליו בכל זאת\n" +
                    "④ יקפוץ חלון — לחץ אישור\n\n" +
                    "⑤ לך להגדרות ← אפליקציות ← SPenGuard\n" +
                    "⑥ לחץ 3 נקודות למעלה שמאל\n" +
                    "⑦ אפשר הגדרות מוגבלות\n\n" +
                    "⑧ חזור לנגישות ← SPenGuard ← הפעל\n" +
                    "⑨ חזור לאפליקציה"
                );
                actionBtn.setText("פתח הגדרות נגישות ←");
                actionBtn.setEnabled(true);
                break;

            case 4:
                startGuard();
                statusIcon.setText("✓");
                statusText.setText(
                    "הגנה פעילה!\n\n" +
                    "מצלם עם המצלמה הקדמית\n" +
                    "מצפצף 5 שניות מעל שקט\n" +
                    "עובד גם עם מסך כבוי\n" +
                    "שומר תמונה לגלריה אוטומטית\n" +
                    "מתחיל אוטומטי אחרי ריסטארט"
                );
                actionBtn.setText("פעיל");
                actionBtn.setEnabled(false);
                break;
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

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isDeviceAdminActive() {
        return dpm != null && dpm.isAdminActive(adminComponent);
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
            if (getPackageName().equals(si.packageName)) return true;
        }
        return false;
    }

    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "SPenGuard צריך הרשאה זו כדי לפעול גם כשהמסך כבוי");
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
        ActivityCompat.requestPermissions(this,
                list.toArray(new String[0]), REQ_PERM);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DEVICE_ADMIN) {
            updateUI();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code,
            @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        updateUI();
    }
}
