package com.jptechgenius.optipause.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.jptechgenius.optipause.alarm.IntervalAlarmManager;
import com.jptechgenius.optipause.repository.TimerRepository;
import com.jptechgenius.optipause.service.TimerForegroundService;

/**
 * BootReceiver
 *
 * Listens for BOOT_COMPLETED and QUICKBOOT_POWERON (HTC devices) broadcasts.
 * When a device reboots, all AlarmManager alarms are cleared by the OS.
 * This receiver checks whether the timer was running before the reboot and,
 * if so, reschedules the next alarm and restarts the foreground service.
 *
 * Required permissions in AndroidManifest:
 *   <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action);

        if (!isBoot) return;

        Log.d(TAG, "Boot completed — checking if timer should be restored.");

        TimerRepository    repository   = new TimerRepository(context);
        IntervalAlarmManager alarmManager = new IntervalAlarmManager(context);

        if (!repository.isRunning()) {
            Log.d(TAG, "Timer was not running before reboot — nothing to restore.");
            return;
        }

        if (!alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms — clearing timer state.");
            repository.clearTimerState();
            return;
        }

        long intervalMillis = repository.getIntervalMillis();

        // Schedule the next alarm starting from NOW (reboot cleared all pending alarms)
        long nextAlarmTime = System.currentTimeMillis() + intervalMillis;
        repository.saveTimerState(true, nextAlarmTime, System.currentTimeMillis());

        alarmManager.scheduleNextAlarm(intervalMillis);
        Log.d(TAG, "Alarm rescheduled after boot. Interval: " + (intervalMillis / 1000) + "s");

        // Restart the foreground service so the persistent notification reappears
        Intent serviceIntent = new Intent(context, TimerForegroundService.class);
        serviceIntent.setAction(TimerForegroundService.ACTION_START);
        serviceIntent.putExtra(TimerForegroundService.EXTRA_INTERVAL_MILLIS, intervalMillis);
        context.startForegroundService(serviceIntent);
        Log.d(TAG, "Foreground service restarted after boot.");
    }
}
