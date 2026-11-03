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
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.davidggjg.spenguard.R;
import com.davidggjg.spenguard.receiver.SPenDeviceAdminReceiver;
import com.davidggjg.spenguard.receiver.SPenReceiver;
import com.davidggjg.spenguard.service.WatchdogService;
import com.davidggjg.spenguard.util.ServiceUtils;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Setup wizard and on/off switch for the guard. */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SPENguard";
    private static final int REQ_PERM = 100;
    private static final int REQ_DEVICE_ADMIN = 200;

    private TextView statusIcon;
    private TextView statusText;
    private Button actionBtn;
    private AdView adView;

    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    private ConsentInformation consentInformation;
    /** MobileAds must be initialised exactly once per process. */
    private final AtomicBoolean adsInitialized = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusIcon = findViewById(R.id.statusIcon);
        statusText = findViewById(R.id.statusText);
        actionBtn = findViewById(R.id.startButton);
        adView = findViewById(R.id.adView);

        dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, SPenDeviceAdminReceiver.class);

        actionBtn.setOnClickListener(v -> handleStep());

        setUpAds();
    }

    // ── Ads and consent ───────────────────────────────────────────────────

    /**
     * Ads are entirely optional: with no network, or if Play services are
     * missing, the banner is hidden and the app keeps working.
     *
     * <p>Before any ad is requested, Google's User Messaging Platform decides
     * whether a consent form is required. In the EEA and the UK AdMob will not
     * serve personalised ads — and may serve none at all — unless consent has
     * been gathered through a certified consent mechanism. Outside those
     * regions UMP reports that no form is needed and ads load immediately.
     */
    private void setUpAds() {
        if (adView == null) {
            return;
        }
        try {
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                    Log.w(TAG, "Ad failed to load: " + adError.getMessage());
                    hideBanner();
                }

                @Override
                public void onAdLoaded() {
                    if (adView != null) {
                        adView.setVisibility(View.VISIBLE);
                    }
                }
            });
            requestConsentThenInitAds();
        } catch (Exception | NoClassDefFoundError e) {
            Log.w(TAG, "Ads unavailable: " + e);
            hideBanner();
            adView = null;
        }
    }

    private void requestConsentThenInitAds() {
        ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build();

        consentInformation = UserMessagingPlatform.getConsentInformation(this);
        consentInformation.requestConsentInfoUpdate(this, params,
                () -> {
                    // Consent state known: show the form only if one is required.
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, formError -> {
                        if (formError != null) {
                            Log.w(TAG, "Consent form: " + formError.getMessage());
                        }
                        maybeInitializeAds();
                    });
                },
                requestError -> {
                    // Offline, or UMP unreachable. Never block the app on this;
                    // canRequestAds() stays false until consent is resolved.
                    Log.w(TAG, "Consent info update failed: " + requestError.getMessage());
                    maybeInitializeAds();
                });

        // If consent was already granted in a previous session, ads can start
        // without waiting for the network round-trip above.
        maybeInitializeAds();
    }

    private void maybeInitializeAds() {
        if (consentInformation == null || !consentInformation.canRequestAds()) {
            return;
        }
        if (!adsInitialized.compareAndSet(false, true)) {
            return;
        }
        // MobileAds.initialize does disk and network I/O; off the main thread
        // so it can never contribute to an ANR.
        new Thread(() -> {
            try {
                MobileAds.initialize(this, status -> runOnUiThread(this::loadBanner));
            } catch (Exception | NoClassDefFoundError e) {
                Log.w(TAG, "MobileAds init failed: " + e);
                runOnUiThread(this::hideBanner);
            }
        }, "SPENguard-AdsInit").start();
    }

    private void loadBanner() {
        if (adView == null || isFinishing() || isDestroyed()) {
            return;
        }
        try {
            adView.loadAd(new AdRequest.Builder().build());
        } catch (Exception | NoClassDefFoundError e) {
            Log.w(TAG, "loadAd failed: " + e);
            hideBanner();
        }
    }

    private void hideBanner() {
        if (adView != null) {
            adView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adView != null) {
            try {
                adView.resume();
            } catch (Exception ignored) {
                // Ad SDK not initialised.
            }
        }
        updateUI();
    }

    @Override
    protected void onPause() {
        if (adView != null) {
            try {
                adView.pause();
            } catch (Exception ignored) {
                // Ad SDK not initialised.
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (adView != null) {
            try {
                adView.destroy();
            } catch (Exception ignored) {
                // Ad SDK not initialised.
            }
            adView = null;
        }
        super.onDestroy();
    }

    // ── Setup wizard ──────────────────────────────────────────────────────

    private int getCurrentStep() {
        if (!hasCameraPermission()) {
            return 1;
        }
        if (!isDeviceAdminActive()) {
            return 2;
        }
        if (!isAccessibilityEnabled()) {
            return 3;
        }
        return 4;
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
                openAccessibilitySettings();
                break;
            case 4:
                enableGuard();
                updateUI();
                break;
            default:
                break;
        }
    }

    private void openAccessibilitySettings() {
        Intent acc = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        acc.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(acc);
        } catch (Exception e) {
            // Some ROMs do not expose this settings screen.
            Log.e(TAG, "Cannot open accessibility settings: " + e);
            Toast.makeText(this, R.string.err_no_accessibility_settings,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void updateUI() {
        switch (getCurrentStep()) {
            case 1:
                statusIcon.setText("1");
                statusText.setText(
                        "שלב 1 מתוך 3\n\n"
                                + "נדרשת הרשאת מצלמה\n"
                                + "לחץ כדי לאשר");
                actionBtn.setText("תן הרשאת מצלמה");
                actionBtn.setEnabled(true);
                break;

            case 2:
                statusIcon.setText("2");
                statusText.setText(
                        "שלב 2 מתוך 3\n\n"
                                + "נדרשת הרשאת מנהל מכשיר\n\n"
                                + "זה מאפשר לאפליקציה לפעול\n"
                                + "גם כשהמסך כבוי\n\n"
                                + "לחץ כדי לאשר");
                actionBtn.setText("אשר מנהל מכשיר");
                actionBtn.setEnabled(true);
                break;

            case 3:
                statusIcon.setText("3");
                statusText.setText(
                        "שלב 3 מתוך 3 — הגדרת נגישות\n\n"
                                + "① לחץ על הכפתור למטה\n"
                                + "② מצא SPENguard ברשימה\n"
                                + "③ אם כתוב חסום — לחץ עליו בכל זאת\n"
                                + "④ יקפוץ חלון — לחץ אישור\n\n"
                                + "⑤ לך להגדרות ← אפליקציות ← SPENguard\n"
                                + "⑥ לחץ 3 נקודות למעלה שמאל\n"
                                + "⑦ אפשר הגדרות מוגבלות\n\n"
                                + "⑧ חזור לנגישות ← SPENguard ← הפעל\n"
                                + "⑨ חזור לאפליקציה");
                actionBtn.setText("פתח הגדרות נגישות ←");
                actionBtn.setEnabled(true);
                break;

            case 4:
                enableGuard();
                statusIcon.setText("✓");
                statusText.setText(
                        "הגנה פעילה!\n\n"
                                + "מצלם עם המצלמה הקדמית\n"
                                + "מצפצף 5 שניות מעל שקט\n"
                                + "עובד גם עם מסך כבוי\n"
                                + "שומר תמונה לגלריה אוטומטית\n"
                                + "מתחיל אוטומטי אחרי ריסטארט");
                actionBtn.setText("פעיל");
                actionBtn.setEnabled(false);
                break;

            default:
                break;
        }
    }

    /** Persists the "armed" flag and makes sure the watchdog is running. */
    private void enableGuard() {
        saveEnabled(true);
        Intent i = new Intent(this, WatchdogService.class);
        i.setAction(WatchdogService.ACTION_START);
        if (!ServiceUtils.startForegroundServiceSafe(this, i)) {
            Toast.makeText(this, R.string.err_service_start, Toast.LENGTH_LONG).show();
        }
    }

    private void saveEnabled(boolean enabled) {
        try {
            getSharedPreferences(SPenReceiver.PREFS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(SPenReceiver.KEY_ENABLED, enabled)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Cannot persist settings: " + e);
        }
    }

    // ── Permission / state checks ─────────────────────────────────────────

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isDeviceAdminActive() {
        try {
            return dpm != null && dpm.isAdminActive(adminComponent);
        } catch (Exception e) {
            Log.e(TAG, "isAdminActive: " + e);
            return false;
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            AccessibilityManager am =
                    (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            if (am == null) {
                return false;
            }
            List<AccessibilityServiceInfo> list =
                    am.getEnabledAccessibilityServiceList(
                            AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            if (list == null) {
                return false;
            }
            for (AccessibilityServiceInfo info : list) {
                if (info == null || info.getResolveInfo() == null) {
                    continue;
                }
                ServiceInfo si = info.getResolveInfo().serviceInfo;
                if (si != null && getPackageName().equals(si.packageName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "isAccessibilityEnabled: " + e);
        }
        return false;
    }

    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.device_admin_explanation));
        try {
            startActivityForResult(intent, REQ_DEVICE_ADMIN);
        } catch (Exception e) {
            Log.e(TAG, "Cannot open device admin screen: " + e);
            Toast.makeText(this, R.string.err_no_device_admin, Toast.LENGTH_LONG).show();
        }
    }

    private void requestPerms() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Only needed to write the photo on Android 9 and older; from
            // Android 10 the photo goes through MediaStore, which needs no
            // storage permission at all.
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        try {
            ActivityCompat.requestPermissions(this, list.toArray(new String[0]), REQ_PERM);
        } catch (Exception e) {
            Log.e(TAG, "requestPermissions: " + e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DEVICE_ADMIN) {
            updateUI();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        updateUI();
    }
}
