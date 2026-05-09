package com.jptechgenius.optipause.repository;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * TimerRepository
 *
 * The single source of truth for persisted user settings and timer state.
 * Uses SharedPreferences so data survives app kills and device reboots.
 *
 * Keys stored:
 *  KEY_INTERVAL_MILLIS  — user-selected interval in milliseconds (default 20 min)
 *  KEY_IS_RUNNING       — whether the timer was active when last saved
 *  KEY_NEXT_ALARM_TIME  — absolute epoch ms of the next scheduled alarm
 *  KEY_START_TIME       — epoch ms when the current interval began
 */
public class TimerRepository {

    private static final String PREFS_NAME   = "optipause_prefs";
    private static final String KEY_INTERVAL = "interval_millis";
    private static final String KEY_RUNNING  = "is_running";
    private static final String KEY_NEXT_ALARM  = "next_alarm_time";
    private static final String KEY_START_TIME  = "start_time";

    /** Default interval: 20 minutes in milliseconds. */
    public static final long DEFAULT_INTERVAL_MILLIS = 20 * 60 * 1000L;

    private final SharedPreferences prefs;

    public TimerRepository(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Interval
    // ─────────────────────────────────────────────────────────────────────────

    /** Persists the user-selected alarm interval. */
    public void saveIntervalMillis(long millis) {
        prefs.edit().putLong(KEY_INTERVAL, millis).apply();
    }

    /** Retrieves the saved interval, defaulting to 20 minutes. */
    public long getIntervalMillis() {
        return prefs.getLong(KEY_INTERVAL, DEFAULT_INTERVAL_MILLIS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Running state
    // ─────────────────────────────────────────────────────────────────────────

    /** Records whether the timer is currently active. */
    public void setRunning(boolean running) {
        prefs.edit().putBoolean(KEY_RUNNING, running).apply();
    }

    /** Returns true if the timer was running when state was last persisted. */
    public boolean isRunning() {
        return prefs.getBoolean(KEY_RUNNING, false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Alarm timing
    // ─────────────────────────────────────────────────────────────────────────

    /** Saves the absolute system time (ms) at which the next alarm will fire. */
    public void saveNextAlarmTime(long epochMillis) {
        prefs.edit().putLong(KEY_NEXT_ALARM, epochMillis).apply();
    }

    /** Returns the saved next-alarm epoch time, or 0 if none saved. */
    public long getNextAlarmTime() {
        return prefs.getLong(KEY_NEXT_ALARM, 0L);
    }

    /** Saves the epoch ms when the current countdown interval started. */
    public void saveStartTime(long epochMillis) {
        prefs.edit().putLong(KEY_START_TIME, epochMillis).apply();
    }

    /** Returns the epoch ms when the interval started, or 0 if none saved. */
    public long getStartTime() {
        return prefs.getLong(KEY_START_TIME, 0L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bulk helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Atomically saves all fields needed to restore state after a reboot.
     * Uses a single edit/apply for efficiency.
     */
    public void saveTimerState(boolean running, long nextAlarmTime, long startTime) {
        prefs.edit()
                .putBoolean(KEY_RUNNING, running)
                .putLong(KEY_NEXT_ALARM, nextAlarmTime)
                .putLong(KEY_START_TIME, startTime)
                .apply();
    }

    /** Clears all timer runtime state (but keeps the saved interval preference). */
    public void clearTimerState() {
        prefs.edit()
                .remove(KEY_RUNNING)
                .remove(KEY_NEXT_ALARM)
                .remove(KEY_START_TIME)
                .apply();
    }
}
