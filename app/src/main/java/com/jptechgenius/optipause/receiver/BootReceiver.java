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
 * Restores the timer state after a device reboot.
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

        TimerRepository repository = new TimerRepository(context);
        IntervalAlarmManager alarmManager = new IntervalAlarmManager(context);

        // If the timer wasn't running, do nothing
        if (!repository.isRunning()) return;

        if (!alarmManager.canScheduleExactAlarms()) {
            repository.clearTimerState();
            return;
        }

        // FIX: Changed getIntervalMillis() to getWorkMillis() or getBreakMillis()
        // depending on the saved mode.
        long intervalMillis = repository.isBreakMode()
                ? repository.getBreakMillis()
                : repository.getWorkMillis();

        long nextAlarmTime = repository.getNextAlarmTime();
        long now = System.currentTimeMillis();

        long remaining;
        if (nextAlarmTime > now) {
            // Restore exact remaining time
            remaining = nextAlarmTime - now;
        } else {
            // If time passed while phone was off, start a fresh cycle
            remaining = intervalMillis;
            repository.saveTimerState(true, now + intervalMillis, now);
        }

        // Reschedule the alarm
        alarmManager.scheduleNextAlarm(remaining);

        // Restart the foreground service to show the notification
        Intent serviceIntent = new Intent(context, TimerForegroundService.class);
        serviceIntent.setAction(TimerForegroundService.ACTION_START);
        serviceIntent.putExtra(TimerForegroundService.EXTRA_INTERVAL_MILLIS, intervalMillis);
        context.startForegroundService(serviceIntent);

        Log.d(TAG, "OptiPause timer restored after reboot. Mode: " +
                (repository.isBreakMode() ? "Break" : "Work"));
    }
}