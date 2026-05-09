package com.jptechgenius.optipause.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.jptechgenius.optipause.R;
import com.jptechgenius.optipause.alarm.IntervalAlarmManager;
import com.jptechgenius.optipause.repository.TimerRepository;
import com.jptechgenius.optipause.ui.FullScreenAlarmActivity;
import com.jptechgenius.optipause.ui.MainActivity;

public class TimerForegroundService extends Service {

    private static final String TAG = "TimerFgService";

    public static final String ACTION_START = "com.jptechgenius.optipause.START_SERVICE";
    public static final String ACTION_STOP = "com.jptechgenius.optipause.STOP_SERVICE";
    public static final String ACTION_ALARM_FIRED = "com.jptechgenius.optipause.ALARM_FIRED";
    public static final String ACTION_DISMISS_ALARM = "com.jptechgenius.optipause.DISMISS_ALARM";
    public static final String ACTION_STATE_CHANGED = "com.jptechgenius.optipause.STATE_CHANGED";
    public static final String EXTRA_INTERVAL_MILLIS = "extra_interval_millis";
    private static final String CHANNEL_ID = "optipause_timer_channel_high";
    private static final int NOTIF_ID = 101;

    private TimerRepository repository;
    private IntervalAlarmManager alarmManager;
    private MediaPlayer mediaPlayer;
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
        if (ACTION_START.equals(action)) {
            isBreakMode = false;
            repository.setBreakMode(false);
            promoteToForeground("Focusing...", "Work session started");
        } else if (ACTION_ALARM_FIRED.equals(action)) {
            handleAlarmFired();
        } else if (ACTION_DISMISS_ALARM.equals(action)) {
            handleDismissAndStartBreak();
        } else if (ACTION_STOP.equals(action)) {
            handleFullStop();
        }
        return START_STICKY;
    }

    private void handleAlarmFired() {
        if (!isBreakMode) {
            playSound(repository.getWorkTone(), true, true);
            // Time's Up holei updateNotification call hobe ja Full Screen Activity trigger korbe
            updateNotification("Time's Up!", "Take a break! Tap to stop alarm.", true);
        } else {
            playSound(repository.getBreakTone(), false, false);
            isBreakMode = false;
            repository.setBreakMode(false);
            long workInterval = repository.getWorkMillis();
            long nextAlarm = System.currentTimeMillis() + workInterval;
            repository.saveTimerState(true, nextAlarm, System.currentTimeMillis());
            alarmManager.scheduleNextAlarm(workInterval);
            sendBroadcast(new Intent(ACTION_STATE_CHANGED));
            updateNotification("Break Over", "Back to work!", false);
        }
    }

    private void handleDismissAndStartBreak() {
        stopAlarmSound();
        isBreakMode = true;
        repository.setBreakMode(true);
        long breakMillis = repository.getBreakMillis();
        long nextAlarm = System.currentTimeMillis() + breakMillis;
        repository.saveTimerState(true, nextAlarm, System.currentTimeMillis());
        alarmManager.scheduleNextAlarm(breakMillis);
        sendBroadcast(new Intent(ACTION_STATE_CHANGED));
        updateNotification("Break Mode", "Resting your eyes...", false);
    }

    private void playSound(String uriString, boolean isAlarmType, boolean isLooping) {
        stopAlarmSound();
        try {
            Uri alertUri = TextUtils.isEmpty(uriString) ?
                    RingtoneManager.getDefaultUri(isAlarmType ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION) :
                    Uri.parse(uriString);

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, alertUri);
            AudioAttributes attr = new AudioAttributes.Builder()
                    .setUsage(isAlarmType ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            mediaPlayer.setAudioAttributes(attr);
            mediaPlayer.setLooping(isLooping);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Sound play error", e);
        }
    }

    private void stopAlarmSound() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
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

    private void updateNotification(String title, String text, boolean showDismiss) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(title, text, showDismiss));
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
                .setCategory(NotificationCompat.CATEGORY_ALARM) // এটাকে অ্যালার্ম ক্যাটাগরি করা হলো
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC); // লক স্ক্রিনে যেন দেখা যায়

        if (showDismissBtn) {
            Log.d("OPTIPAUSE_DEBUG", "Triggering Full Screen Intent now...");

            // Full Screen Intent setup
            Intent fsIntent = new Intent(this, FullScreenAlarmActivity.class);
            fsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            PendingIntent fsPi = PendingIntent.getActivity(this, 4, fsIntent, flags);

            builder.setFullScreenIntent(fsPi, true); // এই লাইনটিই পপআপ ওপেন করে
            builder.setPriority(NotificationCompat.PRIORITY_MAX); // প্রিওরিটি অবশ্যই MAX হতে হবে

            Intent dIntent = new Intent(this, TimerForegroundService.class).setAction(ACTION_DISMISS_ALARM);
            PendingIntent dPi = PendingIntent.getService(this, 2, dIntent, flags);
            builder.addAction(R.drawable.ic_stop, "Stop Alarm & Start Break", dPi);
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_LOW);
            Intent sIntent = new Intent(this, TimerForegroundService.class).setAction(ACTION_STOP);
            PendingIntent sPi = PendingIntent.getService(this, 3, sIntent, flags);
            builder.addAction(R.drawable.ic_stop, "Stop All", sPi);
        }
        return builder.build();
    }

    private void handleFullStop() {
        stopAlarmSound();
        repository.clearTimerState();
        sendBroadcast(new Intent(ACTION_STATE_CHANGED));
        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Importance oboshshoi HIGH hote hobe popup ashonor jonno
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "OptiPause Timer", NotificationManager.IMPORTANCE_HIGH);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}