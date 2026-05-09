package com.jptechgenius.optipause.viewmodel;

import android.app.Application;
import android.content.Intent;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.jptechgenius.optipause.alarm.IntervalAlarmManager;
import com.jptechgenius.optipause.repository.TimerRepository;
import com.jptechgenius.optipause.service.TimerForegroundService;

/**
 * TimerViewModel
 *
 * Owns all UI-observable state for the countdown timer screen.
 * Survives configuration changes (rotation, theme switch) without
 * leaking Context references.
 *
 * LiveData exposed to the UI:
 *  isRunning         — whether the timer is currently active
 *  remainingMillis   — ms left in the current countdown interval
 *  intervalMillis    — the currently selected interval length
 *  progressFraction  — 0.0–1.0 value for the circular progress indicator
 *  exactAlarmGranted — whether SCHEDULE_EXACT_ALARM permission is held
 */
public class TimerViewModel extends AndroidViewModel {

    // ─── LiveData ────────────────────────────────────────────────────────────
    private final MutableLiveData<Boolean> _isRunning        = new MutableLiveData<>(false);
    private final MutableLiveData<Long>    _remainingMillis  = new MutableLiveData<>(0L);
    private final MutableLiveData<Long>    _intervalMillis   = new MutableLiveData<>();
    private final MutableLiveData<Float>   _progressFraction = new MutableLiveData<>(0f);
    private final MutableLiveData<Boolean> _exactAlarmGranted = new MutableLiveData<>(true);
    private final MutableLiveData<String>  _toastMessage     = new MutableLiveData<>();

    public LiveData<Boolean> isRunning()         { return _isRunning; }
    public LiveData<Long>    remainingMillis()   { return _remainingMillis; }
    public LiveData<Long>    intervalMillis()    { return _intervalMillis; }
    public LiveData<Float>   progressFraction()  { return _progressFraction; }
    public LiveData<Boolean> exactAlarmGranted() { return _exactAlarmGranted; }
    public LiveData<String>  toastMessage()      { return _toastMessage; }

    // ─── Dependencies ────────────────────────────────────────────────────────
    private final TimerRepository      repository;
    private final IntervalAlarmManager alarmManager;

    // ─── Internal countdown ──────────────────────────────────────────────────
    private CountDownTimer countDownTimer;
    private final Handler  uiHandler = new Handler(Looper.getMainLooper());

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public TimerViewModel(@NonNull Application application) {
        super(application);
        repository   = new TimerRepository(application);
        alarmManager = new IntervalAlarmManager(application);

        // Restore persisted settings
        long savedInterval = repository.getIntervalMillis();
        _intervalMillis.setValue(savedInterval);
        _exactAlarmGranted.setValue(alarmManager.canScheduleExactAlarms());

        // If the service was running before (e.g. app killed, user re-opens),
        // re-attach the in-memory countdown to the existing alarm.
        if (repository.isRunning()) {
            reattachCountdown();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public commands (called from UI)
    // ─────────────────────────────────────────────────────────────────────────

    /** Starts the interval timer. No-op if already running. */
    public void startTimer() {
        if (Boolean.TRUE.equals(_isRunning.getValue())) return;

        if (!alarmManager.canScheduleExactAlarms()) {
            _exactAlarmGranted.setValue(false);
            _toastMessage.setValue("Please grant Exact Alarm permission in Settings.");
            return;
        }

        long interval = getCurrentInterval();
        long startTime = System.currentTimeMillis();
        long nextAlarm = startTime + interval;

        // Persist state before scheduling (survive immediate kill)
        repository.saveTimerState(true, nextAlarm, startTime);

        // Schedule the exact alarm via AlarmManager
        alarmManager.scheduleNextAlarm(interval);

        // Start the foreground service to keep the process alive
        startForegroundService(interval);

        // Update UI LiveData
        _isRunning.setValue(true);
        startCountdownTick(interval, interval);
    }

    /** Stops the interval timer and cancels all scheduled alarms. */
    public void stopTimer() {
        cancelCountdownTick();
        alarmManager.cancelAlarm();
        stopForegroundService();
        repository.clearTimerState();

        _isRunning.setValue(false);
        _remainingMillis.setValue(getCurrentInterval());
        _progressFraction.setValue(0f);
    }

    /** Toggles start/stop. Convenience method for the FAB / toggle button. */
    public void toggleTimer() {
        if (Boolean.TRUE.equals(_isRunning.getValue())) {
            stopTimer();
        } else {
            startTimer();
        }
    }

    /**
     * Updates the alarm interval.
     * If the timer is running, it is restarted with the new interval.
     *
     * @param minutes The new interval expressed in whole minutes.
     */
    public void setIntervalMinutes(int minutes) {
        long millis = minutes * 60_000L;
        repository.saveIntervalMillis(millis);
        _intervalMillis.setValue(millis);

        // If active, restart so the new interval takes effect immediately
        if (Boolean.TRUE.equals(_isRunning.getValue())) {
            stopTimer();
            startTimer();
        } else {
            _remainingMillis.setValue(millis);
        }
    }

    /**
     * Call this from Activity.onResume() to refresh the permission flag,
     * which the user may have changed via Settings while the app was paused.
     */
    public void refreshPermissionState() {
        _exactAlarmGranted.setValue(alarmManager.canScheduleExactAlarms());
    }

    /**
     * Called by AlarmReceiver (via the Service) when an alarm fires.
     * Re-schedules the next alarm and restarts the in-memory countdown.
     */
    public void onAlarmFired() {
        if (!Boolean.TRUE.equals(_isRunning.getValue())) return;

        long interval = getCurrentInterval();
        long startTime = System.currentTimeMillis();
        long nextAlarm = startTime + interval;

        repository.saveTimerState(true, nextAlarm, startTime);
        alarmManager.scheduleNextAlarm(interval);

        cancelCountdownTick();
        startCountdownTick(interval, interval);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Countdown logic
    // ─────────────────────────────────────────────────────────────────────────

    private void startCountdownTick(long totalMillis, long remainingMs) {
        final long total = totalMillis;
        countDownTimer = new CountDownTimer(remainingMs, 500 /* tick every 500ms */) {
            @Override
            public void onTick(long millisUntilFinished) {
                _remainingMillis.postValue(millisUntilFinished);
                float fraction = (total > 0)
                        ? 1f - ((float) millisUntilFinished / total)
                        : 0f;
                _progressFraction.postValue(fraction);
            }

            @Override
            public void onFinish() {
                // AlarmManager fires the actual alarm; the countdown just drives the UI.
                _remainingMillis.postValue(0L);
                _progressFraction.postValue(1f);
            }
        }.start();
    }

    private void cancelCountdownTick() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    /**
     * Reattaches an in-memory CountDownTimer to an alarm that was already
     * scheduled (e.g. user killed and relaunched the app).
     */
    private void reattachCountdown() {
        long nextAlarm = repository.getNextAlarmTime();
        long interval  = getCurrentInterval();
        long now       = System.currentTimeMillis();
        long remaining = nextAlarm - now;

        if (remaining > 0) {
            _isRunning.setValue(true);
            _remainingMillis.setValue(remaining);
            startCountdownTick(interval, remaining);
        } else {
            // Alarm already passed while app was closed; clean up
            repository.clearTimerState();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Foreground Service control
    // ─────────────────────────────────────────────────────────────────────────

    private void startForegroundService(long intervalMillis) {
        Intent intent = new Intent(getApplication(), TimerForegroundService.class);
        intent.setAction(TimerForegroundService.ACTION_START);
        intent.putExtra(TimerForegroundService.EXTRA_INTERVAL_MILLIS, intervalMillis);
        getApplication().startForegroundService(intent);
    }

    private void stopForegroundService() {
        Intent intent = new Intent(getApplication(), TimerForegroundService.class);
        intent.setAction(TimerForegroundService.ACTION_STOP);
        getApplication().startService(intent); // delivers ACTION_STOP to running service
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private long getCurrentInterval() {
        Long v = _intervalMillis.getValue();
        return (v != null && v > 0) ? v : TimerRepository.DEFAULT_INTERVAL_MILLIS;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelCountdownTick();
    }
}
