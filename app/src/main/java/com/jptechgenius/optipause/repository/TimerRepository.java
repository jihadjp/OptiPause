package com.jptechgenius.optipause.repository;

import android.content.Context;
import android.content.SharedPreferences;

public class TimerRepository {

    private static final String PREFS_NAME = "optipause_prefs";

    // Settings Keys
    private static final String KEY_WORK_MILLIS = "interval_millis";
    private static final String KEY_BREAK_MILLIS = "break_millis";

    // NEW: Ringtone Keys
    private static final String KEY_WORK_TONE = "work_tone_uri";
    private static final String KEY_BREAK_TONE = "break_tone_uri";

    // State Keys
    private static final String KEY_RUNNING = "is_running";
    private static final String KEY_NEXT_ALARM = "next_alarm_time";
    private static final String KEY_START_TIME = "start_time";
    private static final String KEY_IS_BREAK_MODE = "is_break_mode";

    public static final long DEFAULT_WORK_MILLIS = 20 * 60 * 1000L;
    public static final long DEFAULT_BREAK_MILLIS = 2 * 60 * 1000L;

    private final SharedPreferences prefs;

    public TimerRepository(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveWorkMillis(long millis) { prefs.edit().putLong(KEY_WORK_MILLIS, millis).apply(); }
    public long getWorkMillis() { return prefs.getLong(KEY_WORK_MILLIS, DEFAULT_WORK_MILLIS); }

    public void saveBreakMillis(long millis) { prefs.edit().putLong(KEY_BREAK_MILLIS, millis).apply(); }
    public long getBreakMillis() { return prefs.getLong(KEY_BREAK_MILLIS, DEFAULT_BREAK_MILLIS); }

    // --- NEW: RINGTONE METHODS ---
    public void saveWorkTone(String uriStr) { prefs.edit().putString(KEY_WORK_TONE, uriStr).apply(); }
    public String getWorkTone() { return prefs.getString(KEY_WORK_TONE, ""); }

    public void saveBreakTone(String uriStr) { prefs.edit().putString(KEY_BREAK_TONE, uriStr).apply(); }
    public String getBreakTone() { return prefs.getString(KEY_BREAK_TONE, ""); }

    // --- STATE METHODS ---
    public void setRunning(boolean running) { prefs.edit().putBoolean(KEY_RUNNING, running).apply(); }
    public boolean isRunning() { return prefs.getBoolean(KEY_RUNNING, false); }

    public void setBreakMode(boolean isBreak) { prefs.edit().putBoolean(KEY_IS_BREAK_MODE, isBreak).apply(); }
    public boolean isBreakMode() { return prefs.getBoolean(KEY_IS_BREAK_MODE, false); }

    public void saveNextAlarmTime(long epochMillis) { prefs.edit().putLong(KEY_NEXT_ALARM, epochMillis).apply(); }
    public long getNextAlarmTime() { return prefs.getLong(KEY_NEXT_ALARM, 0L); }

    public void saveStartTime(long epochMillis) { prefs.edit().putLong(KEY_START_TIME, epochMillis).apply(); }
    public long getStartTime() { return prefs.getLong(KEY_START_TIME, 0L); }

    public void saveTimerState(boolean running, long nextAlarmTime, long startTime) {
        prefs.edit()
                .putBoolean(KEY_RUNNING, running)
                .putLong(KEY_NEXT_ALARM, nextAlarmTime)
                .putLong(KEY_START_TIME, startTime)
                .apply();
    }

    public void clearTimerState() {
        prefs.edit()
                .remove(KEY_RUNNING)
                .remove(KEY_NEXT_ALARM)
                .remove(KEY_START_TIME)
                .remove(KEY_IS_BREAK_MODE)
                .apply();
    }
}