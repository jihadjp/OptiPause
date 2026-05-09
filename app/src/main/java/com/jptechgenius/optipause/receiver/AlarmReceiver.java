package com.jptechgenius.optipause.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import com.jptechgenius.optipause.alarm.IntervalAlarmManager;
import com.jptechgenius.optipause.repository.TimerRepository;
import com.jptechgenius.optipause.service.TimerForegroundService;

/**
 * AlarmReceiver
 * * Logic:
 * 1. Alarm fires (Work or Break interval ends).
 * 2. It triggers a short vibration.
 * 3. It delegates sound and mode switching to TimerForegroundService.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";
    private static final long[] VIBRATION_PATTERN = {0L, 400L, 200L, 400L};

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);

        if (!IntervalAlarmManager.ACTION_INTERVAL_ALARM.equals(action)) return;

        TimerRepository repository = new TimerRepository(context);

        // Guard: if the timer was stopped manually by user, ignore
        if (!repository.isRunning()) {
            Log.d(TAG, "Timer not running — ignoring alarm.");
            return;
        }

        // 1. Vibrate device
        vibrateDevice(context);

        // 2. Notify Service to handle Alarm Sound and Transition (Work <-> Break)
        // We DO NOT play sound here because we need a way to STOP it.
        // The service will manage the Ringtone object.
        Intent serviceIntent = new Intent(context, TimerForegroundService.class);
        serviceIntent.setAction(TimerForegroundService.ACTION_ALARM_FIRED);
        context.startService(serviceIntent);

        Log.d(TAG, "Alarm triggered. Notifying ForegroundService.");
    }

    @SuppressWarnings("deprecation")
    private void vibrateDevice(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1));
                }
            } else {
                Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1));
                    } else {
                        v.vibrate(VIBRATION_PATTERN, -1);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Vibration failed", e);
        }
    }
}