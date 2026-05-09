package com.jptechgenius.optipause.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jptechgenius.optipause.R;
import com.jptechgenius.optipause.databinding.ActivityMainBinding;
import com.jptechgenius.optipause.viewmodel.TimerViewModel;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private TimerViewModel      viewModel;

    private final ActivityResultLauncher<Intent> exactAlarmSettingsLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> viewModel.refreshPermissionState()
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding   = ActivityMainBinding.inflate(getLayoutInflater());
        viewModel = new ViewModelProvider(this).get(TimerViewModel.class);

        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        observeViewModel();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refreshPermissionState();

        // Android 14 (API 34) Full Screen Intent Permission Check
        if (Build.VERSION.SDK_INT >= 34) { // 34 = UPSIDE_DOWN_CAKE
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Full Screen Permission Required")
                        .setMessage("To wake up your screen during eye breaks, please allow Full Screen Intents.")
                        .setPositiveButton("Allow", (dialog, which) -> {
                            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    private void observeViewModel() {
        // Observe all crucial states to update UI smartly
        viewModel.isRunning().observe(this, running -> updateDynamicUI());
        viewModel.isBreakMode().observe(this, isBreak -> updateDynamicUI());

        viewModel.remainingMillis().observe(this, millis -> {
            binding.tvCountdown.setText(formatCountdown(millis));
            updateDynamicUI(); // Update UI in case time hits 0
        });

        viewModel.progressFraction().observe(this, fraction -> {
            int progress = Math.round(fraction * 100);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                binding.progressCircular.setProgress(progress, true);
            } else {
                binding.progressCircular.setProgress(progress);
            }
        });

        viewModel.intervalMillis().observe(this, millis -> {
            String label;
            if (millis >= 60000) {
                label = (millis / 60000) + " min interval";
            } else {
                label = (millis / 1000) + " sec interval";
            }
            binding.tvInterval.setText(label);
        });

        viewModel.exactAlarmGranted().observe(this, granted -> {
            if (!granted) showExactAlarmPermissionDialog();
        });

        viewModel.toastMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * DYNAMIC UI HANDLER
     * Changes button text, icon and status label based on current mode and remaining time.
     */
    private void updateDynamicUI() {
        boolean isRunning = Boolean.TRUE.equals(viewModel.isRunning().getValue());
        boolean isBreak = Boolean.TRUE.equals(viewModel.isBreakMode().getValue());
        long remaining = viewModel.remainingMillis().getValue() != null ? viewModel.remainingMillis().getValue() : 0L;

        if (isRunning) {
            if (remaining <= 0) {
                // Time is Up! Waiting for user to click Stop/Dismiss
                binding.btnStartStop.setText("Stop Alarm & Start Break");
                binding.btnStartStop.setIconResource(R.drawable.ic_play);
                binding.tvStatus.setText("Time's up! Tap to stop alarm.");
            } else {
                // Normal Running State
                binding.btnStartStop.setText(R.string.stop_timer);
                binding.btnStartStop.setIconResource(R.drawable.ic_stop);
                binding.tvStatus.setText(isBreak ? "Break Mode (Resting...)" : "Work Mode (Focusing...)");
            }
        } else {
            // Idle State
            binding.btnStartStop.setText(R.string.start_timer);
            binding.btnStartStop.setIconResource(R.drawable.ic_play);
            binding.tvStatus.setText(R.string.status_idle);
            binding.progressCircular.setProgress(0);
        }
    }

    private void setupClickListeners() {
        binding.btnStartStop.setOnClickListener(v -> viewModel.toggleTimer());

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                openSettings();
                return true;
            }
            return false;
        });

    }

    private void openSettings() {
        SettingsBottomSheet sheet = new SettingsBottomSheet();
        sheet.show(getSupportFragmentManager(), SettingsBottomSheet.TAG);
    }

    private void showExactAlarmPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Permission Required")
                .setMessage("OptiPause needs 'Alarms & Reminders' permission for exact eye-break timing.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent intent = new Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + getPackageName())
                        );
                        exactAlarmSettingsLauncher.launch(intent);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String formatCountdown(long millis) {
        if (millis <= 0) return "00:00";
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}