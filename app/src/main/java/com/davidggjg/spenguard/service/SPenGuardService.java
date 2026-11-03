package com.davidggjg.spenguard.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.davidggjg.spenguard.R;
import com.davidggjg.spenguard.ui.MainActivity;
import com.davidggjg.spenguard.ui.StopAlarmActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Sounds the alarm and photographs the scene when the S Pen is removed.
 */
public class SPenGuardService extends Service {

    private static final String TAG = "SPENguard";
    public static final String ACTION_SPEN_REMOVED = "com.davidggjg.spenguard.SPEN_REMOVED";
    public static final String ACTION_STOP = "com.davidggjg.spenguard.STOP";

    private static final String CHANNEL_ID = "spenguard_alert";
    private static final int NOTIF_ID = 1001;
    private static final int REPEAT_INTERVAL_MS = 30_000;
    private static final int MAX_DURATION_MS = 5 * 60 * 1000;
    private static final int BEEP_DURATION_MS = 5_000;
    private static final int CAMERA_TIMEOUT_MS = 10_000;

    private static final int SAMPLE_RATE = 44100;
    private static final double FREQUENCY = 1000.0;

    /**
     * True while the alarm service is live. Used by receivers so they never
     * start the service purely in order to stop it, which on Android 8+ would
     * mean a foreground service that never calls startForeground().
     */
    private static volatile boolean active = false;

    public static boolean isActive() {
        return active;
    }

    private volatile boolean isRunning = false;
    private AudioTrack audioTrack;
    private Thread alarmThread;
    private Handler mainHandler;
    private PowerManager.WakeLock wakeLock;

    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The service may have been launched with startForegroundService(), in
        // which case startForeground() MUST be called on every delivery -
        // including a null (restarted) intent - or the system kills the app
        // with ForegroundServiceDidNotStartInTimeException.
        if (!promoteToForeground()) {
            stopEverything();
            return START_NOT_STICKY;
        }

        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            Log.i(TAG, "SPenGuardService: STOP");
            stopEverything();
            return START_NOT_STICKY;
        }

        if (ACTION_SPEN_REMOVED.equals(action) && !isRunning) {
            isRunning = true;
            active = true;
            acquireWakeLock();
            showStopScreen();
            startAlarm();
            capturePhoto();
            mainHandler.postDelayed(this::stopEverything, MAX_DURATION_MS);
        } else if (action == null && !isRunning) {
            // Nothing to do (e.g. a stray restart) - do not linger as a
            // foreground service with no alarm running.
            stopEverything();
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopEverything();
        super.onDestroy();
    }

    /** Idempotent: safe to call on every onStartCommand. */
    private boolean promoteToForeground() {
        Notification notification = buildNotification();
        int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
        if (hasCameraPermission()) {
            type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
        }
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, type);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "startForeground with camera type failed: " + e);
        }
        // Android 14 forbids starting a *camera* foreground service from the
        // background. Fall back to media playback only - the alarm is the
        // important part; the photo is best effort.
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "startForeground refused entirely: " + e);
            return false;
        }
    }

    public void stopEverything() {
        isRunning = false;
        active = false;
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        stopAlarm();
        closeCamera();
        releaseWakeLock();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ── Screen / wake lock ────────────────────────────────────────────────

    /**
     * Brings up {@link StopAlarmActivity}. Since Android 10 a background app
     * cannot simply call startActivity(), so the full-screen intent attached
     * to the notification is the reliable path; the direct call is kept for
     * the cases where it is still permitted (e.g. launched from the
     * accessibility service).
     */
    private void showStopScreen() {
        try {
            Intent popup = new Intent(this, StopAlarmActivity.class);
            popup.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(popup);
        } catch (Exception e) {
            Log.w(TAG, "startActivity refused, relying on full-screen intent: " + e);
        }
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) {
            return;
        }
        try {
            wakeLock = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "SPENguard:AlarmLock");
            wakeLock.acquire(MAX_DURATION_MS);
        } catch (Exception e) {
            // FULL_WAKE_LOCK is deprecated and rejected by some OEM ROMs.
            Log.w(TAG, "FULL_WAKE_LOCK unavailable, falling back to PARTIAL: " + e);
            try {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SPENguard:AlarmLock");
                wakeLock.acquire(MAX_DURATION_MS);
            } catch (Exception e2) {
                Log.e(TAG, "No wake lock could be acquired: " + e2);
                wakeLock = null;
            }
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {
            // Already released.
        }
        wakeLock = null;
    }

    // ── Alarm tone ────────────────────────────────────────────────────────

    private void startAlarm() {
        raiseAlarmVolume();

        final Thread thread = new Thread(() -> {
            AudioTrack track = null;
            try {
                int minBuffer = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                if (minBuffer <= 0) {
                    Log.e(TAG, "AudioTrack.getMinBufferSize failed: " + minBuffer);
                    return;
                }
                int samples = minBuffer * 4;

                short[] buffer = new short[samples];
                for (int i = 0; i < samples; i++) {
                    buffer[i] = (short) (Short.MAX_VALUE
                            * Math.sin(2.0 * Math.PI * FREQUENCY * i / SAMPLE_RATE));
                }

                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();

                AudioFormat format = new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build();

                track = new AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(samples * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();

                audioTrack = track;
                track.play();

                while (isRunning && !Thread.currentThread().isInterrupted()) {
                    if (track.write(buffer, 0, buffer.length) < 0) {
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Alarm tone failed: " + e);
            } finally {
                // Only this thread owns `track`; stopAlarm() may have already
                // cleared the field, so never dereference it here.
                if (track != null) {
                    try {
                        track.stop();
                    } catch (Exception ignored) {
                        // Not playing.
                    }
                    try {
                        track.release();
                    } catch (Exception ignored) {
                        // Already released.
                    }
                }
                if (audioTrack == track) {
                    audioTrack = null;
                }
            }

            if (isRunning) {
                mainHandler.postDelayed(() -> {
                    if (isRunning) {
                        startAlarm();
                    }
                }, REPEAT_INTERVAL_MS);
            }
        }, "SPENguard-Alarm");

        alarmThread = thread;
        thread.setDaemon(true);
        thread.start();

        mainHandler.postDelayed(this::stopAlarm, BEEP_DURATION_MS);
    }

    private void raiseAlarmVolume() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return;
        }
        try {
            am.setStreamVolume(AudioManager.STREAM_ALARM,
                    am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        } catch (SecurityException e) {
            // Do Not Disturb is active and the app has no policy access.
            Log.w(TAG, "Cannot raise alarm volume: " + e);
        } catch (Exception e) {
            Log.w(TAG, "setStreamVolume failed: " + e);
        }
    }

    /** Stops the current beep. The repeat timer, if any, is unaffected. */
    private void stopAlarm() {
        Thread thread = alarmThread;
        alarmThread = null;
        if (thread != null) {
            thread.interrupt();
        }
        AudioTrack track = audioTrack;
        audioTrack = null;
        if (track != null) {
            try {
                track.stop();
            } catch (Exception ignored) {
                // Not playing.
            }
        }
        // The worker thread releases the track in its finally block.
    }

    // ── Camera ────────────────────────────────────────────────────────────

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void capturePhoto() {
        if (cameraManager == null) {
            Log.w(TAG, "No CameraManager - skipping photo");
            return;
        }
        if (!hasCameraPermission()) {
            Log.w(TAG, "CAMERA permission not granted - skipping photo");
            return;
        }

        cameraThread = new HandlerThread("SPENguard-Camera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        // Never leave the camera open if a callback never arrives.
        mainHandler.postDelayed(this::closeCamera, CAMERA_TIMEOUT_MS);

        cameraHandler.post(() -> {
            try {
                String id = getFrontCameraId();
                if (id == null) {
                    Log.w(TAG, "No front camera on this device");
                    closeCamera();
                    return;
                }

                imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
                imageReader.setOnImageAvailableListener(reader -> {
                    try (Image img = reader.acquireLatestImage()) {
                        if (img != null) {
                            saveImage(img);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Reading image failed: " + e);
                    }
                    closeCamera();
                }, cameraHandler);

                cameraManager.openCamera(id, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice cam) {
                        cameraDevice = cam;
                        startSession();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice cam) {
                        cam.close();
                        cameraDevice = null;
                    }

                    @Override
                    public void onError(@NonNull CameraDevice cam, int e) {
                        Log.e(TAG, "Camera error " + e);
                        cam.close();
                        cameraDevice = null;
                    }
                }, cameraHandler);

            } catch (CameraAccessException | SecurityException
                     | IllegalArgumentException | IllegalStateException e) {
                Log.e(TAG, "Camera open failed: " + e);
                closeCamera();
            }
        });
    }

    private void startSession() {
        CameraDevice device = cameraDevice;
        ImageReader reader = imageReader;
        if (device == null || reader == null) {
            return;
        }
        try {
            device.createCaptureSession(Arrays.asList(reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession s) {
                            captureSession = s;
                            shoot();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession s) {
                            Log.e(TAG, "Capture session configuration failed");
                            closeCamera();
                        }
                    }, cameraHandler);
        } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
            Log.e(TAG, "startSession: " + e);
            closeCamera();
        }
    }

    private void shoot() {
        CameraCaptureSession session = captureSession;
        CameraDevice device = cameraDevice;
        ImageReader reader = imageReader;
        if (session == null || device == null || reader == null) {
            return;
        }
        try {
            CaptureRequest.Builder b =
                    device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(reader.getSurface());
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            session.capture(b.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession s,
                                               @NonNull CaptureRequest r,
                                               @NonNull TotalCaptureResult res) {
                    Log.i(TAG, "Photo taken");
                }
            }, cameraHandler);
        } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
            Log.e(TAG, "shoot: " + e);
            closeCamera();
        }
    }

    private void saveImage(Image image) {
        ByteBuffer buf = image.getPlanes()[0].getBuffer();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String name = "SPENguard_" + ts + ".jpg";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/SPENguard");
                cv.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
                Uri uri = getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) {
                    Log.e(TAG, "MediaStore insert returned null");
                    return;
                }
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        os.write(data);
                    }
                }
            } catch (IOException | IllegalArgumentException | IllegalStateException
                     | SecurityException e) {
                Log.e(TAG, "Save failed: " + e);
            }
        } else {
            try {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "SPENguard");
                if (!dir.exists() && !dir.mkdirs()) {
                    Log.e(TAG, "Cannot create " + dir);
                    return;
                }
                try (FileOutputStream fos = new FileOutputStream(new File(dir, name))) {
                    fos.write(data);
                }
            } catch (IOException | SecurityException e) {
                Log.e(TAG, "Save failed: " + e);
            }
        }
    }

    @Nullable
    private String getFrontCameraId() throws CameraAccessException {
        for (String id : cameraManager.getCameraIdList()) {
            Integer facing = cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return id;
            }
        }
        return null;
    }

    private synchronized void closeCamera() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
        } catch (Exception ignored) {
            captureSession = null;
        }
        try {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (Exception ignored) {
            cameraDevice = null;
        }
        try {
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception ignored) {
            imageReader = null;
        }
        try {
            if (cameraThread != null) {
                cameraThread.quitSafely();
                cameraThread = null;
                cameraHandler = null;
            }
        } catch (Exception ignored) {
            cameraThread = null;
            cameraHandler = null;
        }
    }

    // ── Notification ──────────────────────────────────────────────────────

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        ch.setDescription(getString(R.string.channel_alert_desc));
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        PendingIntent stopIntent = PendingIntent.getService(this, 0,
                new Intent(this, SPenGuardService.class).setAction(ACTION_STOP), flags);

        PendingIntent openIntent = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class), flags);

        Intent fullScreen = new Intent(this, StopAlarmActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreenIntent = PendingIntent.getActivity(this, 2, fullScreen, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.alert_title))
                .setContentText(getString(R.string.alert_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openIntent)
                .setFullScreenIntent(fullScreenIntent, true)
                .addAction(R.drawable.ic_notification, getString(R.string.action_stop), stopIntent)
                .setOngoing(true)
                .build();
    }
}
