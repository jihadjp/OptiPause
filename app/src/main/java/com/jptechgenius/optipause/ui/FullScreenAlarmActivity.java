package com.jptechgenius.optipause.ui;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.jptechgenius.optipause.databinding.ActivityFullScreenAlarmBinding;
import com.jptechgenius.optipause.service.TimerForegroundService;

public class FullScreenAlarmActivity extends AppCompatActivity {

    private ActivityFullScreenAlarmBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Aggressive Wake Up & Lock Screen Bypass
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            android.app.KeyguardManager keyguardManager = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        }

        binding = com.jptechgenius.optipause.databinding.ActivityFullScreenAlarmBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnStopAndBreak.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.jptechgenius.optipause.service.TimerForegroundService.class);
            intent.setAction(com.jptechgenius.optipause.service.TimerForegroundService.ACTION_DISMISS_ALARM);
            startService(intent);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}