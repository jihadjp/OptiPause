package com.jptechgenius.optipause.viewmodel;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.CountDownTimer;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.jptechgenius.optipause.alarm.IntervalAlarmManager;
import com.jptechgenius.optipause.repository.TimerRepository;
import com.jptechgenius.optipause.service.TimerForegroundService;

public class TimerViewModel extends AndroidViewModel {

    private final MutableLiveData<Boolean> _isRunning        = new MutableLiveData<>(false);
    private final MutableLiveData<Long>    _remainingMillis  = new MutableLiveData<>(0L);
    private final MutableLiveData<Long>    _intervalMillis   = new MutableLiveData<>();
    private final MutableLiveData<Float>   _progressFraction = new MutableLiveData<>(0f);
    private final MutableLiveData<Boolean> _exactAlarmGranted = new MutableLiveData<>(true);
    private final MutableLiveData<String>  _toastMessage     = new MutableLiveData<>();

    // NEW: Track if we are currently in Break Mode
    private final MutableLiveData<Boolean> _isBreakMode      = new MutableLiveData<>(false);

    public LiveData<Boolean> isRunning()         { return _isRunning; }
    public LiveData<Long>    remainingMillis()   { return _remainingMillis; }
    public LiveData<Long>    intervalMillis()    { return _intervalMillis; }
    public LiveData<Float>   progressFraction()  { return _progressFraction; }
    public LiveData<Boolean> exactAlarmGranted() { return _exactAlarmGranted; }
    public LiveData<String>  toastMessage()      { return _toastMessage; }
    public LiveData<Boolean> isBreakMode()       { return _isBreakMode; } // NEW

    private final TimerRepository      repository;
    private final IntervalAlarmManager alarmManager;
    private CountDownTimer countDownTimer;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TimerForegroundService.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                reattachCountdown();
            }
        }
    };

    public TimerViewModel(@NonNull Application application) {
        super(application);
        repository   = new TimerRepository(application);
        alarmManager = new IntervalAlarmManager(application);

        IntentFilter filter = new IntentFilter(TimerForegroundService.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            application.registerReceiver(stateReceiver, filter);
        }

        long currentInterval = getCurrentInterval();
        _intervalMillis.setValue(currentInterval);
        _remainingMillis.setValue(currentInterval);
        _exactAlarmGranted.setValue(alarmManager.canScheduleExactAlarms());
        _isBreakMode.setValue(repository.isBreakMode());

        if (repository.isRunning()) {
            reattachCountdown();
        }
    }

    public void startTimer() {
        if (Boolean.TRUE.equals(_isRunning.getValue())) return;

        if (!alarmManager.canScheduleExactAlarms()) {
            _exactAlarmGranted.setValue(false);
            _toastMessage.setValue("Please grant Exact Alarm permission.");
            return;
        }

        repository.setBreakMode(false);
        long interval = repository.getWorkMillis();
        long startTime = System.currentTimeMillis();
        long nextAlarm = startTime + interval;

        repository.saveTimerState(true, nextAlarm, startTime);
        alarmManager.scheduleNextAlarm(interval);
        startForegroundService(interval);

        _isRunning.setValue(true);
        _isBreakMode.setValue(false);
        _intervalMillis.setValue(interval);
        startCountdownTick(interval, interval);
    }

    public void stopTimer() {
        cancelCountdownTick();
        alarmManager.cancelAlarm();
        stopForegroundService();
        repository.clearTimerState();

        _isRunning.setValue(false);
        _isBreakMode.setValue(false);
        long workInterval = repository.getWorkMillis();
        _intervalMillis.setValue(workInterval);
        _remainingMillis.setValue(workInterval);
        _progressFraction.setValue(0f);
    }

    // UPDATED LOGIC: Smart Toggle
    public void toggleTimer() {
        if (Boolean.TRUE.equals(_isRunning.getValue())) {
            Long currentRemaining = _remainingMillis.getValue();
            if (currentRemaining != null && currentRemaining <= 0L) {
                // Time is 00:00 and Alarm is ringing -> Dismiss Alarm & Go to next phase
                Intent intent = new Intent(getApplication(), TimerForegroundService.class);
                intent.setAction(TimerForegroundService.ACTION_DISMISS_ALARM);
                getApplication().startService(intent);
            } else {
                // Timer is running normally -> Full Stop
                stopTimer();
            }
        } else {
            startTimer();
        }
    }

    public void refreshStateFromSettings() {
        if (!Boolean.TRUE.equals(_isRunning.getValue())) {
            long workInterval = repository.getWorkMillis();
            _intervalMillis.setValue(workInterval);
            _remainingMillis.setValue(workInterval);
            _progressFraction.setValue(0f);
        }
    }

    public void refreshPermissionState() {
        _exactAlarmGranted.setValue(alarmManager.canScheduleExactAlarms());
    }

    public void reattachCountdown() {
        boolean running = repository.isRunning();
        _isRunning.postValue(running);
        _isBreakMode.postValue(repository.isBreakMode()); // Sync break state

        if (!running) {
            cancelCountdownTick();
            long workInterval = repository.getWorkMillis();
            _intervalMillis.postValue(workInterval);
            _remainingMillis.postValue(workInterval);
            _progressFraction.postValue(0f);
            return;
        }

        long nextAlarm = repository.getNextAlarmTime();
        long now = System.currentTimeMillis();
        long remaining = nextAlarm - now;
        long totalInterval = getCurrentInterval();

        if (remaining > 0) {
            _intervalMillis.postValue(totalInterval);
            startCountdownTick(totalInterval, remaining);
        } else {
            _remainingMillis.postValue(0L);
            _progressFraction.postValue(1f);
        }
    }

    private void startCountdownTick(long totalMillis, long remainingMs) {
        final long total = totalMillis;
        cancelCountdownTick();

        countDownTimer = new CountDownTimer(remainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                _remainingMillis.postValue(millisUntilFinished);
                float fraction = (total > 0) ? 1f - ((float) millisUntilFinished / total) : 0f;
                _progressFraction.postValue(fraction);
            }

            @Override
            public void onFinish() {
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

    private void startForegroundService(long intervalMillis) {
        Intent intent = new Intent(getApplication(), TimerForegroundService.class);
        intent.setAction(TimerForegroundService.ACTION_START);
        intent.putExtra(TimerForegroundService.EXTRA_INTERVAL_MILLIS, intervalMillis);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication().startForegroundService(intent);
        } else {
            getApplication().startService(intent);
        }
    }

    private void stopForegroundService() {
        Intent intent = new Intent(getApplication(), TimerForegroundService.class);
        intent.setAction(TimerForegroundService.ACTION_STOP);
        getApplication().startService(intent);
    }

    private long getCurrentInterval() {
        return repository.isBreakMode() ? repository.getBreakMillis() : repository.getWorkMillis();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelCountdownTick();
        try {
            getApplication().unregisterReceiver(stateReceiver);
        } catch (Exception ignored) {}
    }
}