package com.jptechgenius.optipause.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.jptechgenius.optipause.R;
import com.jptechgenius.optipause.alarm.IntervalAlarmManager;
import com.jptechgenius.optipause.repository.TimerRepository;
import com.jptechgenius.optipause.ui.MainActivity;

/**
 * TimerForegroundService
 * Manages the Two-Stage Eye Care Cycle:
 * 1. Work Mode: Uses user-defined work interval.
 * 2. Alarm Mode: Continuous sound until manually dismissed.
 * 3. Break Mode: Uses user-defined break interval, then auto-restarts work.
 */
public class TimerForegroundService extends Service {

    private static final String TAG = "TimerFgService";

    // Intent Actions
    public static final String ACTION_START = "com.jptechgenius.optipause.START_SERVICE";
    public static final String ACTION_STOP = "com.jptechgenius.optipause.STOP_SERVICE";
    public static final String ACTION_ALARM_FIRED = "com.jptechgenius.optipause.ALARM_FIRED";
    public static final String ACTION_DISMISS_ALARM = "com.jptechgenius.optipause.DISMISS_ALARM";

    // UI Update Action
    public static final String ACTION_STATE_CHANGED = "com.jptechgenius.optipause.STATE_CHANGED";

    public static final String EXTRA_INTERVAL_MILLIS = "extra_interval_millis";

    private static final String CHANNEL_ID = "optipause_timer_channel";
    private static final int NOTIF_ID = 101;

    private TimerRepository repository;
    private IntervalAlarmManager alarmManager;
    private Ringtone ringtone;
    private boolean isBreakMode = false;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new TimerRepository(this);
        alarmManager = new IntervalAlarmManager(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // Restore state if killed by system
            if (repository.isRunning()) {
                isBreakMode = repository.isBreakMode();
                promoteToForeground(isBreakMode ? "Resting..." : "Focusing...",
                        isBreakMode ? "Break in progress" : "Work in progress");
            } else {
                stopSelf();
            }
            return START_STICKY;
        }

        String action = intent.getAction();
        if (action == null) return START_STICKY;

        switch (action) {
            case ACTION_START:
                isBreakMode = false;
                repository.setBreakMode(false);
                promoteToForeground("Focusing...", "Work session started");
                break;

            case ACTION_ALARM_FIRED:
                handleAlarmFired();
                break;

            case ACTION_DISMISS_ALARM:
                handleDismissAndStartBreak();
                break;

            case ACTION_STOP:
                handleFullStop();
                break;
        }

        return START_STICKY;
    }

    private void handleAlarmFired() {
        if (!isBreakMode) {
            // Work finished -> Play looping alarm
            playAlarmSound();
            updateNotification("Time's Up!", "Take a break! Tap to stop alarm.", true);
        } else {
            // Break finished -> Auto-restart work mode
            isBreakMode = false;
            repository.setBreakMode(false);

            long workInterval = repository.getWorkMillis(); // Dynamic work time
            long nextAlarm = System.currentTimeMillis() + workInterval;

            // Save state for Dashboard countdown
            repository.saveTimerState(true, nextAlarm, System.currentTimeMillis());
            alarmManager.scheduleNextAlarm(workInterval);

            // Notify UI to update the countdown circle
            sendBroadcast(new Intent(ACTION_STATE_CHANGED));

            updateNotification("Break Over", "Back to work! Focus session started.", false);
        }
    }

    private void handleDismissAndStartBreak() {
        stopAlarmSound();
        isBreakMode = true;
        repository.setBreakMode(true); // Tell repository we are in break mode

        long breakMillis = repository.getBreakMillis(); // Dynamic break time
        long nextAlarm = System.currentTimeMillis() + breakMillis;

        // Save state so MainActivity dashboard can show the break countdown
        repository.saveTimerState(true, nextAlarm, System.currentTimeMillis());
        alarmManager.scheduleNextAlarm(breakMillis);

        // Notify UI to update the countdown circle to Break Time
        sendBroadcast(new Intent(ACTION_STATE_CHANGED));

        updateNotification("Break Mode", "Resting your eyes...", false);
    }

    private void playAlarmSound() {
        try {
            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alert == null) alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

            ringtone = RingtoneManager.getRingtone(getApplicationContext(), alert);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.setLooping(true);
                }
                ringtone.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing sound", e);
        }
    }

    private void stopAlarmSound() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
    }

    private void promoteToForeground(String title, String text) {
        Notification notification = buildNotification(title, text, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void updateNotification(String title, String text, boolean showDismissBtn) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildNotification(title, text, showDismissBtn));
        }
    }

    private Notification buildNotification(String title, String text, boolean showDismissBtn) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_timer_notification) // Ensure this icon exists in drawable
                .setContentIntent(openPi)
                .setOngoing(true)
                .setPriority(showDismissBtn ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_LOW);

        if (showDismissBtn) {
            Intent dismissIntent = new Intent(this, TimerForegroundService.class);
            dismissIntent.setAction(ACTION_DISMISS_ALARM);
            PendingIntent dismissPi = PendingIntent.getService(this, 2, dismissIntent, flags);
            builder.addAction(R.drawable.ic_stop, "Stop Alarm & Start Break", dismissPi);
        } else {
            // Add a Stop button to the notification to kill the service manually
            Intent stopIntent = new Intent(this, TimerForegroundService.class);
            stopIntent.setAction(ACTION_STOP);
            PendingIntent stopPi = PendingIntent.getService(this, 3, stopIntent, flags);
            builder.addAction(R.drawable.ic_stop, "Stop All", stopPi);
        }

        return builder.build();
    }

    private void handleFullStop() {
        stopAlarmSound();
        repository.clearTimerState();
        sendBroadcast(new Intent(ACTION_STATE_CHANGED)); // UI-কে Stop হওয়ার সিগন্যাল দিচ্ছে
        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "OptiPause Timer", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}