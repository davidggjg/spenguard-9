package com.davidggjg.spenguard.ui;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
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

    // שלבים
    private static final int STEP_PERMISSION = 1;
    private static final int STEP_OPEN_ACCESSIBILITY = 2;
    private static final int STEP_ALLOW_RESTRICTED = 3;
    private static final int STEP_ENABLE_SERVICE = 4;
    private static final int STEP_DONE = 5;

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

        actionBtn.setOnClickListener(v -> handleStep());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private int getCurrentStep() {
        if (!hasCameraPermission()) return STEP_PERMISSION;
        if (!isAccessibilityEnabled()) {
            // בדוק אם המשתמש כבר ניסה לפתוח נגישות
            // אם הנגישות לא פעילה — אנחנו בשלב 2 או 3 או 4
            // נניח שלב 2 תמיד — המשתמש יתקדם
            return STEP_OPEN_ACCESSIBILITY;
        }
        return STEP_DONE;
    }

    private void handleStep() {
        int step = getCurrentStep();
        switch (step) {
            case STEP_PERMISSION:
                requestPerms();
                break;

            case STEP_OPEN_ACCESSIBILITY:
                // פתח נגישות ישירות
                Intent acc = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                acc.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(acc);
                break;

            case STEP_DONE:
                startGuard();
                break;
        }
    }

    private void updateUI() {
        int step = getCurrentStep();

        switch (step) {
            case STEP_PERMISSION:
                statusIcon.setText("1");
                statusText.setText("שלב 1 מתוך 3\n\nנדרשת הרשאת מצלמה\nלחץ כדי לאשר");
                actionBtn.setText("תן הרשאת מצלמה");
                actionBtn.setEnabled(true);
                break;

            case STEP_OPEN_ACCESSIBILITY:
                // בדוק אם הכפתור הקודם כבר לחץ
                // הצג את כל שלושת השלבים הנגישות
                statusIcon.setText("2");
                statusText.setText(
                    "שלב 2 מתוך 3 — הגדרת נגישות\n\n" +
                    "① לחץ על הכפתור למטה\n" +
                    "② מצא SPenGuard ברשימה\n" +
                    "③ אם כתוב חסום — לחץ עליו בכל זאת\n" +
                    "④ תקבל הודעה על גישה חסומה — לחץ אישור\n\n" +
                    "⑤ עכשיו לחץ כפתור למטה שוב\n" +
                    "⑥ לך לאפליקציות ← SPenGuard\n" +
                    "⑦ 3 נקודות למעלה שמאל\n" +
                    "⑧ אפשר הגדרות מוגבלות\n\n" +
                    "⑨ חזור לנגישות ← SPenGuard ← הפעל"
                );
                actionBtn.setText("פתח הגדרות נגישות ←");
                actionBtn.setEnabled(true);
                break;

            case STEP_DONE:
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
