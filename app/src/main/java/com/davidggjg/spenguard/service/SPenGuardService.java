package com.davidggjg.spenguard.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.AudioManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.davidggjg.spenguard.ui.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class SPenGuardService extends Service {

    private static final String TAG = "SPenGuard";
    public static final String ACTION_SPEN_REMOVED = "com.davidggjg.spenguard.SPEN_REMOVED";

    private static final String CHANNEL_ID   = "spenguard_alert";
    private static final int    NOTIF_ID     = 1001;
    private static final int    ALARM_MS     = 5000;

    private CameraManager      cameraManager;
    private CameraDevice       cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader        imageReader;
    private HandlerThread      cameraThread;
    private Handler            cameraHandler;
    private ToneGenerator      toneGenerator;
    private Handler            mainHandler;

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler   = new Handler(Looper.getMainLooper());
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SPEN_REMOVED.equals(intent.getAction())) {
            startForeground(NOTIF_ID, buildNotification());
            playAlarm();
            capturePhoto();
        }
        return START_NOT_STICKY;
    }

    @Nullable @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        stopAlarm();
        closeCamera();
        super.onDestroy();
    }

    // ── Alarm ─────────────────────────────────────────────────────────────

    private void playAlarm() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setStreamVolume(AudioManager.STREAM_ALARM,
                        am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            }
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ALARM_MS);
        } catch (Exception e) {
            Log.e(TAG, "Alarm error: " + e.getMessage());
        }
        // Stop service after alarm ends
        mainHandler.postDelayed(() -> {
            stopAlarm();
            stopSelf();
        }, ALARM_MS + 300);
    }

    private void stopAlarm() {
        if (toneGenerator != null) {
            try { toneGenerator.stopTone(); toneGenerator.release(); } catch (Exception ignored) {}
            toneGenerator = null;
        }
    }

    // ── Camera ────────────────────────────────────────────────────────────

    private void capturePhoto() {
        cameraThread = new HandlerThread("cam");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        cameraHandler.post(() -> {
            try {
                String id = getFrontCameraId();
                if (id == null) { Log.w(TAG, "No front camera"); return; }

                imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
                imageReader.setOnImageAvailableListener(reader -> {
                    Image img = reader.acquireLatestImage();
                    if (img != null) { saveImage(img); img.close(); }
                    closeCamera();
                }, cameraHandler);

                cameraManager.openCamera(id, new CameraDevice.StateCallback() {
                    @Override public void onOpened(@NonNull CameraDevice cam) {
                        cameraDevice = cam;
                        startSession();
                    }
                    @Override public void onDisconnected(@NonNull CameraDevice cam) { cam.close(); }
                    @Override public void onError(@NonNull CameraDevice cam, int e) {
                        Log.e(TAG, "Camera error " + e); cam.close();
                    }
                }, cameraHandler);

            } catch (CameraAccessException | SecurityException e) {
                Log.e(TAG, "Camera open: " + e.getMessage());
            }
        });
    }

    private void startSession() {
        if (cameraDevice == null || imageReader == null) return;
        try {
            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(@NonNull CameraCaptureSession s) {
                            captureSession = s;
                            shoot();
                        }
                        @Override public void onConfigureFailed(@NonNull CameraCaptureSession s) {
                            Log.e(TAG, "Session config failed");
                        }
                    }, cameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "startSession: " + e.getMessage());
        }
    }

    private void shoot() {
        if (captureSession == null || cameraDevice == null) return;
        try {
            CaptureRequest.Builder b = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(imageReader.getSurface());
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            b.set(CaptureRequest.CONTROL_AE_MODE,  CaptureRequest.CONTROL_AE_MODE_ON);
            captureSession.capture(b.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(@NonNull CameraCaptureSession s,
                        @NonNull CaptureRequest r, @NonNull TotalCaptureResult res) {
                    Log.i(TAG, "Photo taken");
                }
            }, cameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "shoot: " + e.getMessage());
        }
    }

    private void saveImage(Image image) {
        ByteBuffer buf = image.getPlanes()[0].getBuffer();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);

        String ts   = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String name = "SPenGuard_" + ts + ".jpg";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ — save directly to gallery via MediaStore
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/SPenGuard");
            cv.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri != null) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) os.write(data);
                    Log.i(TAG, "Saved to gallery: " + uri);
                } catch (IOException e) {
                    Log.e(TAG, "MediaStore write: " + e.getMessage());
                }
            }
        } else {
            // Android 9 and below
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "SPenGuard");
            if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(new File(dir, name))) {
                fos.write(data);
                Log.i(TAG, "Saved: " + dir + "/" + name);
            } catch (IOException e) {
                Log.e(TAG, "File write: " + e.getMessage());
            }
        }
    }

    @Nullable
    private String getFrontCameraId() throws CameraAccessException {
        for (String id : cameraManager.getCameraIdList()) {
            Integer facing = cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) return id;
        }
        return null;
    }

    private void closeCamera() {
        try {
            if (captureSession != null) { captureSession.close(); captureSession = null; }
            if (cameraDevice   != null) { cameraDevice.close();   cameraDevice   = null; }
            if (imageReader    != null) { imageReader.close();     imageReader    = null; }
            if (cameraThread   != null) { cameraThread.quitSafely(); cameraThread = null; }
        } catch (Exception ignored) {}
    }

    // ── Notification ──────────────────────────────────────────────────────

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "SPenGuard התראה", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🚨 SPenGuard — S Pen הוסר!")
                .setContentText("מצלם ומצפצף 5 שניות...")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
