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
 * * Logic:
 * When device restarts, all alarms are cleared.
 * This restores the timer if it was active before reboot.
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

        if (!repository.isRunning()) return;

        if (!alarmManager.canScheduleExactAlarms()) {
            repository.clearTimerState();
            return;
        }

        // Restore interval and check timing
        long intervalMillis = repository.getIntervalMillis();
        long nextAlarmTime = repository.getNextAlarmTime();
        long now = System.currentTimeMillis();

        long remaining;
        if (nextAlarmTime > now) {
            // Restore remaining time
            remaining = nextAlarmTime - now;
        } else {
            // If the time passed during reboot, start a fresh cycle
            remaining = intervalMillis;
            repository.saveTimerState(true, now + intervalMillis, now);
        }

        alarmManager.scheduleNextAlarm(remaining);

        // Restart service to keep notification alive
        Intent serviceIntent = new Intent(context, TimerForegroundService.class);
        serviceIntent.setAction(TimerForegroundService.ACTION_START);
        serviceIntent.putExtra(TimerForegroundService.EXTRA_INTERVAL_MILLIS, intervalMillis);
        context.startForegroundService(serviceIntent);

        Log.d(TAG, "OptiPause state restored after reboot.");
    }
}