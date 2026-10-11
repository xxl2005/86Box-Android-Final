package net.box86.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps the emulator process in foreground priority only while an external
 * Android document picker is covering the VM.  A 512 MB+ guest is otherwise
 * a very attractive cached-process kill target as soon as DocumentsUI takes
 * the screen, which used to discard the live VM when the user merely browsed
 * a folder and pressed Back.
 */
public final class VmKeepAliveService extends Service {
    public static final String EXTRA_PROFILE_NAME = "profile_name";
    public static final String EXTRA_REQUEST_CODE = "request_code";
    private static final String TAG = "VmKeepAlive";
    private static final String CHANNEL_ID = "86box_vm_picker_keepalive";
    private static final int NOTIFICATION_ID = 8609;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "86Box VM file picker",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the running PC emulator alive while Android's file picker is open.");
            channel.setSound(null, null);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String profile = intent == null ? null : intent.getStringExtra(EXTRA_PROFILE_NAME);
        int request = intent == null ? -1 : intent.getIntExtra(EXTRA_REQUEST_CODE, -1);
        String text = (profile == null || profile.trim().isEmpty())
                ? "VM paused while selecting a file"
                : profile + " paused while selecting a file";

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(R.drawable.icon_86box)
                .setContentTitle("86Box VM kept active")
                .setContentText(text)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        DiagnosticLogger.i(TAG, "Foreground VM keep-alive active profile=" + profile + " request=" + request);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        DiagnosticLogger.i(TAG, "Foreground VM keep-alive stopped");
        if (Build.VERSION.SDK_INT >= 24)
            stopForeground(STOP_FOREGROUND_REMOVE);
        else
            stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
