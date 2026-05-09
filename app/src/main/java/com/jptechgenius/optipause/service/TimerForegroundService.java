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

public class TimerForegroundService extends Service {

    private static final String TAG = "TimerFgService";

    // Intent Actions
    public static final String ACTION_START = "com.jptechgenius.optipause.START_SERVICE";
    public static final String ACTION_STOP = "com.jptechgenius.optipause.STOP_SERVICE";
    public static final String ACTION_ALARM_FIRED = "com.jptechgenius.optipause.ALARM_FIRED";
    public static final String ACTION_DISMISS_ALARM = "com.jptechgenius.optipause.DISMISS_ALARM";

    // FIX: In BootReceiver the variable EXTRA_INTERVAL_MILLIS was not found
    public static final String EXTRA_INTERVAL_MILLIS = "extra_interval_millis"; //

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
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        if (action == null) return START_STICKY;

        switch (action) {
            case ACTION_START:
                isBreakMode = false;
                promoteToForeground("Focusing...", "Work in progress");
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
            playAlarmSound();
            updateNotification("Time's Up!", "Take a break! Tap to stop alarm.", true);
        } else {
            isBreakMode = false;
            long workInterval = repository.getIntervalMillis();
            alarmManager.scheduleNextAlarm(workInterval);
            updateNotification("Break Over", "Back to work!", false);
        }
    }

    private void handleDismissAndStartBreak() {
        stopAlarmSound();
        isBreakMode = true;

        // Manual setup for break duration (e.g., 2 minutes)
//        long breakMillis = 2 * 60 * 1000L;
        long breakMillis = 10 * 1000L; //for test 10 sec
        alarmManager.scheduleNextAlarm(breakMillis);

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
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_timer_notification)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setPriority(showDismissBtn ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_LOW);

        if (showDismissBtn) {
            Intent dismissIntent = new Intent(this, TimerForegroundService.class);
            dismissIntent.setAction(ACTION_DISMISS_ALARM);
            PendingIntent dismissPi = PendingIntent.getService(this, 2, dismissIntent, flags);
            builder.addAction(R.drawable.ic_stop, "Stop Alarm & Start Break", dismissPi);
        }

        return builder.build();
    }

    private void handleFullStop() {
        stopAlarmSound();
        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "OptiPause Timer", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}