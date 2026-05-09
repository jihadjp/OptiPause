package com.jptechgenius.optipause.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.jptechgenius.optipause.R;
import com.jptechgenius.optipause.repository.TimerRepository;
import com.jptechgenius.optipause.ui.MainActivity;

/**
 * TimerForegroundService
 *
 * A foreground Service that keeps the app process alive while the interval
 * timer is active. Without this, Android's battery optimiser may kill the
 * process between alarms, causing missed reminders.
 *
 * The service displays a persistent notification so the user always knows
 * the timer is running and can quickly stop it.
 *
 * Lifecycle:
 *  startService(ACTION_START) → startForeground() → show notification
 *  startService(ACTION_STOP)  → stopForeground()  → stopSelf()
 *
 * AlarmReceiver sends ACTION_ALARM_FIRED to this service when each
 * interval expires, so the service can update the notification text.
 */
public class TimerForegroundService extends Service {

    private static final String TAG = "TimerFgService";

    // ─── Intent actions ──────────────────────────────────────────────────────
    public static final String ACTION_START       = "com.jptechgenius.optipause.START_SERVICE";
    public static final String ACTION_STOP        = "com.jptechgenius.optipause.STOP_SERVICE";
    public static final String ACTION_ALARM_FIRED = "com.jptechgenius.optipause.ALARM_FIRED";
    public static final String ACTION_STOP_FROM_NOTIFICATION =
            "com.jptechgenius.optipause.STOP_FROM_NOTIFICATION";

    // ─── Extras ──────────────────────────────────────────────────────────────
    public static final String EXTRA_INTERVAL_MILLIS = "extra_interval_millis";

    // ─── Notification ────────────────────────────────────────────────────────
    private static final String CHANNEL_ID   = "optipause_timer_channel";
    private static final String CHANNEL_NAME = "OptiPause Timer";
    private static final int    NOTIF_ID     = 101;

    // ─── State ───────────────────────────────────────────────────────────────
    private TimerRepository repository;
    private long intervalMillis;
    private int  alarmCount = 0;

    // ─────────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new TimerRepository(this);
        createNotificationChannel();
        Log.d(TAG, "Service created.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // Restarted by system — re-read state from repository
            if (repository.isRunning()) {
                intervalMillis = repository.getIntervalMillis();
                promoteToForeground();
            } else {
                stopSelf();
            }
            return START_STICKY;
        }

        String action = intent.getAction();
        if (action == null) action = "";

        switch (action) {
            case ACTION_START:
                intervalMillis = intent.getLongExtra(
                        EXTRA_INTERVAL_MILLIS,
                        TimerRepository.DEFAULT_INTERVAL_MILLIS);
                alarmCount = 0;
                promoteToForeground();
                break;

            case ACTION_STOP:
            case ACTION_STOP_FROM_NOTIFICATION:
                handleStop();
                break;

            case ACTION_ALARM_FIRED:
                alarmCount++;
                updateNotification();
                break;

            default:
                Log.w(TAG, "Unknown action: " + action);
        }

        // START_STICKY: system restarts this service if killed, passing null intent
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Foreground promotion
    // ─────────────────────────────────────────────────────────────────────────

    private void promoteToForeground() {
        Notification notification = buildNotification(
                "Timer is running",
                formatInterval(intervalMillis) + " interval · Tap to open");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: declare the foreground service type in AndroidManifest
            startForeground(NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, notification);
        }
        Log.d(TAG, "Promoted to foreground.");
    }

    private void handleStop() {
        stopForeground(true);
        stopSelf();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW  // silent but visible
            );
            channel.setDescription("Shows while the OptiPause interval timer is active.");
            channel.setShowBadge(false);

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void updateNotification() {
        String title = "Reminder triggered! (" + alarmCount + "×)";
        String text  = "Next alarm in " + formatInterval(intervalMillis);
        Notification notification = buildNotification(title, text);

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, notification);
    }

    private Notification buildNotification(String title, String text) {
        // Tapping the notification opens MainActivity
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int openFlags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent, openFlags);

        // "Stop" action in the notification shade
        Intent stopIntent = new Intent(this, TimerForegroundService.class);
        stopIntent.setAction(ACTION_STOP_FROM_NOTIFICATION);
        int stopFlags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent, stopFlags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_timer_notification)
                .setContentIntent(openPi)
                .setOngoing(true)               // cannot be dismissed by swipe
                .setShowWhen(false)
                .addAction(R.drawable.ic_stop, "Stop", stopPi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private String formatInterval(long millis) {
        long totalSeconds = millis / 1000;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %02dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %02ds", minutes, seconds);
        } else {
            return seconds + "s";
        }
    }
}
