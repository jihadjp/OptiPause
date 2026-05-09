package com.jptechgenius.optipause.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
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
 *
 * A BroadcastReceiver that wakes up when the AlarmManager fires the
 * interval alarm. Responsibilities:
 *
 *  1. Play the notification ringtone to alert the user.
 *  2. Vibrate the device.
 *  3. Notify the ForegroundService that an alarm has fired (updates notification).
 *  4. Schedule the NEXT alarm via IntervalAlarmManager so the cycle continues.
 *
 * This runs on the main thread in a short BroadcastReceiver window (~10 s).
 * All work done here must complete quickly; heavy work is delegated to the Service.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    /** Vibration pattern: wait 0ms, vibrate 400ms, pause 200ms, vibrate 400ms */
    private static final long[] VIBRATION_PATTERN = {0L, 400L, 200L, 400L};

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);

        if (!IntervalAlarmManager.ACTION_INTERVAL_ALARM.equals(action)) return;

        TimerRepository repository = new TimerRepository(context);

        // Guard: if the timer was stopped externally, do nothing
        if (!repository.isRunning()) {
            Log.d(TAG, "Timer not running — ignoring alarm.");
            return;
        }

        // 1. Play alarm sound
        playAlarmSound(context);

        // 2. Vibrate
        vibrateDevice(context);

        // 3. Tell the foreground service an alarm fired (updates persistent notification)
        notifyService(context);

        // 4. Schedule the next alarm so the cycle continues
        long intervalMillis = repository.getIntervalMillis();
        long nextAlarmTime  = System.currentTimeMillis() + intervalMillis;
        repository.saveTimerState(true, nextAlarmTime, System.currentTimeMillis());

        IntervalAlarmManager alarmManager = new IntervalAlarmManager(context);
        alarmManager.scheduleNextAlarm(intervalMillis);

        Log.d(TAG, "Next alarm scheduled in " + (intervalMillis / 1000) + "s");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sound
    // ─────────────────────────────────────────────────────────────────────────

    private void playAlarmSound(Context context) {
        try {
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            android.media.Ringtone ringtone = RingtoneManager.getRingtone(context, soundUri);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.setLooping(false);
                }
                ringtone.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing alarm sound", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vibration
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private void vibrateDevice(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm =
                        (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    Vibrator v = vm.getDefaultVibrator();
                    VibrationEffect effect = VibrationEffect.createWaveform(
                            VIBRATION_PATTERN, -1 /* no repeat */);
                    v.vibrate(effect);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    VibrationEffect effect = VibrationEffect.createWaveform(
                            VIBRATION_PATTERN, -1);
                    v.vibrate(effect);
                }
            } else {
                Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(VIBRATION_PATTERN, -1);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error vibrating device", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Service notification
    // ─────────────────────────────────────────────────────────────────────────

    private void notifyService(Context context) {
        Intent serviceIntent = new Intent(context, TimerForegroundService.class);
        serviceIntent.setAction(TimerForegroundService.ACTION_ALARM_FIRED);
        // Use startService — the service is already foregrounded, this just delivers the action
        context.startService(serviceIntent);
    }
}
