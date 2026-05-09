package com.jptechgenius.optipause.repository;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * TimerRepository
 *
 * The single source of truth for persisted user settings and timer state.
 * Modified to support dynamic Work and Break intervals for eye care.
 */
public class TimerRepository {

    private static final String PREFS_NAME = "optipause_prefs";

    // Settings Keys
    private static final String KEY_WORK_MILLIS = "interval_millis";
    private static final String KEY_BREAK_MILLIS = "break_millis";

    // State Keys
    private static final String KEY_RUNNING = "is_running";
    private static final String KEY_NEXT_ALARM = "next_alarm_time";
    private static final String KEY_START_TIME = "start_time";
    private static final String KEY_IS_BREAK_MODE = "is_break_mode";

    /** Default intervals: 20 minutes Work, 2 minutes Break. */
    public static final long DEFAULT_WORK_MILLIS = 20 * 60 * 1000L;
    public static final long DEFAULT_BREAK_MILLIS = 2 * 60 * 1000L;

    private final SharedPreferences prefs;

    public TimerRepository(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User Settings (Work & Break Durations)
    // ─────────────────────────────────────────────────────────────────────────

    /** Persists the user-selected work interval. */
    public void saveWorkMillis(long millis) {
        prefs.edit().putLong(KEY_WORK_MILLIS, millis).apply();
    }

    /** Retrieves the saved work interval, defaulting to 20 minutes. */
    public long getWorkMillis() {
        return prefs.getLong(KEY_WORK_MILLIS, DEFAULT_WORK_MILLIS);
    }

    /** Persists the user-selected break interval. */
    public void saveBreakMillis(long millis) {
        prefs.edit().putLong(KEY_BREAK_MILLIS, millis).apply();
    }

    /** Retrieves the saved break interval, defaulting to 2 minutes. */
    public long getBreakMillis() {
        return prefs.getLong(KEY_BREAK_MILLIS, DEFAULT_BREAK_MILLIS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timer State (For Dashboard & Reboot Recovery)
    // ─────────────────────────────────────────────────────────────────────────

    /** Records whether the timer is currently active. */
    public void setRunning(boolean running) {
        prefs.edit().putBoolean(KEY_RUNNING, running).apply();
    }

    /** Returns true if the timer was running when state was last persisted. */
    public boolean isRunning() {
        return prefs.getBoolean(KEY_RUNNING, false);
    }

    /** Tracks if the current countdown is for a Break or Work. */
    public void setBreakMode(boolean isBreak) {
        prefs.edit().putBoolean(KEY_IS_BREAK_MODE, isBreak).apply();
    }

    /** Returns true if the current active timer is a break. */
    public boolean isBreakMode() {
        return prefs.getBoolean(KEY_IS_BREAK_MODE, false);
    }

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
     * Atomically saves all fields needed to restore state after a reboot or app kill.
     * Use this whenever a new interval (Work or Break) starts.
     */
    public void saveTimerState(boolean running, long nextAlarmTime, long startTime) {
        prefs.edit()
                .putBoolean(KEY_RUNNING, running)
                .putLong(KEY_NEXT_ALARM, nextAlarmTime)
                .putLong(KEY_START_TIME, startTime)
                .apply();
    }

    /** Clears all timer runtime state (keeps user preferences like work/break durations). */
    public void clearTimerState() {
        prefs.edit()
                .remove(KEY_RUNNING)
                .remove(KEY_NEXT_ALARM)
                .remove(KEY_START_TIME)
                .remove(KEY_IS_BREAK_MODE)
                .apply();
    }
}