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
import com.jptechgenius.optipause.repository.TimerRepository;
import com.jptechgenius.optipause.viewmodel.TimerViewModel;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * MainActivity
 *
 * The single-Activity entry point for OptiPause.
 * Uses View Binding + MVVM — zero business logic lives here.
 *
 * UI components:
 *  - CircularProgressIndicator  → countdown progress
 *  - TextView (countdown)       → mm:ss remaining
 *  - ExtendedFAB / ToggleButton → Start / Stop
 *  - MaterialToolbar action     → opens SettingsBottomSheet
 *
 * Material 3 theme applied via themes.xml (Theme.Material3.DayNight).
 */
public class MainActivity extends AppCompatActivity
        implements SettingsBottomSheet.OnIntervalSelectedListener {

    // ─── View Binding & ViewModel ────────────────────────────────────────────
    private ActivityMainBinding binding;
    private TimerViewModel      viewModel;

    // ─── Permission launcher (Android 12+) ───────────────────────────────────
    private final ActivityResultLauncher<Intent> exactAlarmSettingsLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> viewModel.refreshPermissionState()
            );

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

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
        // Re-check permission — user may have granted/revoked it via System Settings
        viewModel.refreshPermissionState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LiveData observation
    // ─────────────────────────────────────────────────────────────────────────

    private void observeViewModel() {

        // ── Running state → toggle button appearance ──────────────────────────
        viewModel.isRunning().observe(this, running -> {
            if (running) {
                binding.btnStartStop.setText(R.string.stop_timer);
                binding.btnStartStop.setIconResource(R.drawable.ic_stop);
                binding.tvStatus.setText(R.string.status_running);
            } else {
                binding.btnStartStop.setText(R.string.start_timer);
                binding.btnStartStop.setIconResource(R.drawable.ic_play);
                binding.tvStatus.setText(R.string.status_idle);
                // Reset progress when stopped
                binding.progressCircular.setProgress(0);
            }
        });

        // ── Remaining ms → countdown label ───────────────────────────────────
        viewModel.remainingMillis().observe(this, millis -> {
            binding.tvCountdown.setText(formatCountdown(millis));
        });

        // ── Progress fraction → CircularProgressIndicator (0–100) ────────────
        viewModel.progressFraction().observe(this, fraction -> {
            int progress = Math.round(fraction * 100);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                binding.progressCircular.setProgress(progress, true); // animated
            } else {
                binding.progressCircular.setProgress(progress);
            }
        });

        // ── Interval → subtitle label ─────────────────────────────────────────
        viewModel.intervalMillis().observe(this, millis -> {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
            String label = minutes + " min interval";
            binding.tvInterval.setText(label);
            // Reset countdown display to full interval when not running
            Boolean running = viewModel.isRunning().getValue();
            if (running == null || !running) {
                binding.tvCountdown.setText(formatCountdown(millis));
            }
        });

        // ── Exact alarm permission ─────────────────────────────────────────────
        viewModel.exactAlarmGranted().observe(this, granted -> {
            if (!granted) showExactAlarmPermissionDialog();
        });

        // ── Toast messages ────────────────────────────────────────────────────
        viewModel.toastMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Click listeners
    // ─────────────────────────────────────────────────────────────────────────

    private void setupClickListeners() {

        // Start / Stop extended FAB
        binding.btnStartStop.setOnClickListener(v -> viewModel.toggleTimer());

        // Settings icon in toolbar → opens SettingsBottomSheet
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                openSettings();
                return true;
            }
            return false;
        });

        // Settings button (optional secondary button)
        if (binding.btnSettings != null) {
            binding.btnSettings.setOnClickListener(v -> openSettings());
        }
    }

    private void openSettings() {
        Long intervalMillis = viewModel.intervalMillis().getValue();
        long minutes = (intervalMillis != null)
                ? TimeUnit.MILLISECONDS.toMinutes(intervalMillis)
                : 20;

        SettingsBottomSheet sheet = SettingsBottomSheet.newInstance((int) minutes);
        sheet.setOnIntervalSelectedListener(this);
        sheet.show(getSupportFragmentManager(), SettingsBottomSheet.TAG);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SettingsBottomSheet callback
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onIntervalSelected(int minutes) {
        viewModel.setIntervalMinutes(minutes);
        Toast.makeText(this,
                "Interval set to " + minutes + " minute" + (minutes == 1 ? "" : "s"),
                Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permission handling
    // ─────────────────────────────────────────────────────────────────────────

    private void showExactAlarmPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Permission Required")
                .setMessage("OptiPause needs the 'Alarms & Reminders' permission to trigger " +
                        "exact interval reminders. Please enable it in Settings.")
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

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Formats milliseconds into a human-readable mm:ss or hh:mm:ss string.
     */
    private String formatCountdown(long millis) {
        if (millis <= 0) return "00:00";

        long totalSeconds = millis / 1000;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }
}
