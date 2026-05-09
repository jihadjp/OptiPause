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
 * * Modified for Manual Eye Breaks:
 * 1. Alarm fires after 20 mins.
 * 2. ViewModel stops and waits for user.
 * 3. User takes a break and manually clicks "Start" for the next session.
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
        _remainingMillis.setValue(savedInterval); // Default display
        _exactAlarmGranted.setValue(alarmManager.canScheduleExactAlarms());

        // Re-attach if was running (e.g., config change)
        if (repository.isRunning()) {
            reattachCountdown();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public commands
    // ─────────────────────────────────────────────────────────────────────────

    /** Starts a single 20-min session. User must trigger this manually each time. */
    public void startTimer() {
        if (Boolean.TRUE.equals(_isRunning.getValue())) return;

        if (!alarmManager.canScheduleExactAlarms()) {
            _exactAlarmGranted.setValue(false);
            _toastMessage.setValue("Please grant Exact Alarm permission.");
            return;
        }

        long interval = getCurrentInterval();
        long startTime = System.currentTimeMillis();
        long nextAlarm = startTime + interval;

        // Persist state
        repository.saveTimerState(true, nextAlarm, startTime);

        // Schedule one-time alarm
        alarmManager.scheduleNextAlarm(interval);

        // Start service to keep app alive during the 20 mins
        startForegroundService(interval);

        _isRunning.setValue(true);
        startCountdownTick(interval, interval);
    }

    /** Manually stops and resets everything. Used when user finishes work. */
    public void stopTimer() {
        cancelCountdownTick();
        alarmManager.cancelAlarm();
        stopForegroundService();
        repository.clearTimerState();

        _isRunning.setValue(false);
        _remainingMillis.setValue(getCurrentInterval());
        _progressFraction.setValue(0f);
    }

    public void toggleTimer() {
        if (Boolean.TRUE.equals(_isRunning.getValue())) {
            stopTimer();
        } else {
            startTimer();
        }
    }

    public void setIntervalMinutes(int minutes) {
        long millis = minutes * 1000L;
        repository.saveIntervalMillis(millis);
        _intervalMillis.setValue(millis);

        // Reset UI even if not running
        if (Boolean.FALSE.equals(_isRunning.getValue())) {
            _remainingMillis.setValue(millis);
            _progressFraction.setValue(0f);
        }
    }

    public void refreshPermissionState() {
        _exactAlarmGranted.setValue(alarmManager.canScheduleExactAlarms());
    }

    /** * UPDATED: Called when alarm fires.
     * Resets the UI to "Idle" state so user can take a break.
     */
    public void onAlarmFired() {
        _isRunning.postValue(false);
        _remainingMillis.postValue(getCurrentInterval());
        _progressFraction.postValue(0f);

        // We do NOT reschedule here. User must click "Start" again.
        cancelCountdownTick();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Countdown logic
    // ─────────────────────────────────────────────────────────────────────────

    private void startCountdownTick(long totalMillis, long remainingMs) {
        final long total = totalMillis;
        cancelCountdownTick(); // Clean up old timer

        countDownTimer = new CountDownTimer(remainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                _remainingMillis.postValue(millisUntilFinished);
                float fraction = (total > 0) ? 1f - ((float) millisUntilFinished / total) : 0f;
                _progressFraction.postValue(fraction);
            }

            @Override
            public void onFinish() {
                onAlarmFired();
            }
        }.start();
    }

    private void cancelCountdownTick() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private void reattachCountdown() {
        long nextAlarm = repository.getNextAlarmTime();
        long interval  = getCurrentInterval();
        long now       = System.currentTimeMillis();
        long remaining = nextAlarm - now;

        if (remaining > 0) {
            _isRunning.setValue(true);
            startCountdownTick(interval, remaining);
        } else {
            repository.clearTimerState();
            _isRunning.setValue(false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Service control
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
        getApplication().startService(intent);
    }

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