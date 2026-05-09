package com.jptechgenius.optipause.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.jptechgenius.optipause.receiver.AlarmReceiver;

/**
 * IntervalAlarmManager
 *
 * A helper class responsible for scheduling and cancelling exact alarms
 * using Android's AlarmManager. Handles API-level differences and
 * SCHEDULE_EXACT_ALARM permission requirements for Android 12+ (API 31+).
 *
 * Design:
 *  - Uses AlarmManager.setExactAndAllowWhileIdle() for Doze-mode compatibility.
 *  - Falls back to setExact() on older APIs.
 *  - PendingIntent uses FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE for Android 12+ safety.
 */
public class IntervalAlarmManager {

    private static final String TAG = "IntervalAlarmManager";

    /** Action broadcast by AlarmReceiver when an interval alarm fires. */
    public static final String ACTION_INTERVAL_ALARM =
            "com.jptechgenius.optipause.ACTION_INTERVAL_ALARM";

    /** Request code for the PendingIntent — must be unique per alarm. */
    private static final int ALARM_REQUEST_CODE = 1001;

    private final Context context;
    private final AlarmManager alarmManager;

    public IntervalAlarmManager(Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager = (AlarmManager) this.context.getSystemService(Context.ALARM_SERVICE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Schedules the next alarm to fire exactly {@code intervalMillis} ms from now.
     *
     * @param intervalMillis The delay in milliseconds before the next alarm fires.
     */
    public void scheduleNextAlarm(long intervalMillis) {
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null — cannot schedule alarm.");
            return;
        }

        long triggerAtMillis = System.currentTimeMillis() + intervalMillis;
        PendingIntent pendingIntent = buildAlarmPendingIntent();

        if (canScheduleExactAlarms()) {
            // API 23+: setExactAndAllowWhileIdle fires even during Doze mode
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
            );
            Log.d(TAG, "Exact alarm scheduled (setExactAndAllowWhileIdle) for +"
                    + intervalMillis + "ms");
        } else {
            // Fallback for very old APIs (pre-23) — less precise but functional
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
            );
            Log.d(TAG, "Exact alarm scheduled (setExact) for +" + intervalMillis + "ms");
        }
    }

    /**
     * Cancels any pending interval alarm.
     * Safe to call even if no alarm is currently scheduled.
     */
    public void cancelAlarm() {
        if (alarmManager == null) return;
        PendingIntent pendingIntent = buildAlarmPendingIntent();
        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
        Log.d(TAG, "Alarm cancelled.");
    }

    /**
     * Returns whether the app is allowed to schedule exact alarms.
     *
     * On Android 12 (API 31) and above, the user must grant
     * SCHEDULE_EXACT_ALARM permission. On earlier versions this
     * is always true.
     */
    public boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return alarmManager != null && alarmManager.canScheduleExactAlarms();
        }
        // Below API 31 exact alarms are always permitted
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the PendingIntent that points to {@link AlarmReceiver}.
     *
     * FLAG_UPDATE_CURRENT  — reuse any existing PendingIntent, updating its extras.
     * FLAG_IMMUTABLE       — required on Android 12+ for security.
     */
    private PendingIntent buildAlarmPendingIntent() {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_INTERVAL_ALARM);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags);
    }
}
