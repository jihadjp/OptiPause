package com.jptechgenius.optipause.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jptechgenius.optipause.R;
import com.jptechgenius.optipause.databinding.ActivityMainBinding;
import com.jptechgenius.optipause.viewmodel.TimerViewModel;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private TimerViewModel viewModel;

    // ── Session trackers ──────────────────────────────────────────────────────
    private int sessionCount = 0;
    private boolean wasBreak = false; // Tracks transition to prevent multiple counts

    // Double-click guard for settings
    private long lastSettingsClickTime = 0;
    private ValueAnimator pulseAnimator;

    private final ActivityResultLauncher<Intent> exactAlarmLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> viewModel.refreshPermissionState()
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        viewModel = new ViewModelProvider(this).get(TimerViewModel.class);
        setContentView(binding.getRoot());

        setupClickListeners();
        observeViewModel();
        animateEntrance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refreshPermissionState();
        checkFullScreenIntentPermission();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pulseAnimator != null) pulseAnimator.cancel();
        binding = null;
    }

    private void observeViewModel() {
        viewModel.isRunning().observe(this, running -> refreshDynamicUI());
        viewModel.isBreakMode().observe(this, isBreak -> refreshDynamicUI());

        viewModel.remainingMillis().observe(this, millis -> {
            binding.tvCountdown.setText(formatTime(millis));
            refreshDynamicUI();
        });

        viewModel.progressFraction().observe(this, fraction -> {
            int progress = Math.round(fraction * 100);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                binding.progressCircular.setProgress(progress, true);
                binding.progressGlow.setProgress(progress, true);
            } else {
                binding.progressCircular.setProgress(progress);
                binding.progressGlow.setProgress(progress);
            }
        });

        viewModel.intervalMillis().observe(this, millis -> {
            String label = millis >= 60_000
                    ? (millis / 60_000) + " min"
                    : (millis / 1_000) + " sec";
            binding.tvInterval.setText(label);
        });

        viewModel.exactAlarmGranted().observe(this, granted -> {
            if (!granted) showExactAlarmDialog();
        });

        viewModel.toastMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty())
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
    }

    private void refreshDynamicUI() {
        boolean isRunning = Boolean.TRUE.equals(viewModel.isRunning().getValue());
        boolean isBreak = Boolean.TRUE.equals(viewModel.isBreakMode().getValue());
        long remaining = viewModel.remainingMillis().getValue() != null
                ? viewModel.remainingMillis().getValue() : 0L;

        // ── NEW: Proper Session Counting Logic ──
        // Only increment when transitioning from Work (wasBreak = false) to Break (isBreak = true)
        if (isRunning && isBreak && !wasBreak) {
            sessionCount++;
            binding.tvSessionCount.setText(String.valueOf(sessionCount));
        }
        // Save the current state for the next UI refresh
        wasBreak = isBreak;

        if (isRunning) {
            if (remaining <= 0) {
                setStatusPill("Time's up! Take a break.", R.drawable.bg_status_dot_break);
                setFab("Stop Alarm", R.drawable.ic_stop);
                showModeBadge("ALARM", true);
                setRingColor(true);
                stopPulse();
                flashRing();
            } else if (isBreak) {
                setStatusPill("Break Mode (Resting...)", R.drawable.bg_status_dot_break);
                setFab(getString(R.string.stop_timer), R.drawable.ic_stop);
                showModeBadge("BREAK", true);
                setRingColor(true);
                startPulse();
            } else {
                setStatusPill("Work Mode (Focusing...)", R.drawable.bg_status_dot_running);
                setFab(getString(R.string.stop_timer), R.drawable.ic_stop);
                showModeBadge("WORK", false);
                setRingColor(false);
                startPulse();
            }
        } else {
            setStatusPill(getString(R.string.status_idle), R.drawable.bg_status_dot_idle);
            setFab(getString(R.string.start_timer), R.drawable.ic_play);
            showModeBadge("", false);
            setRingColor(false);
            stopPulse();
            animateProgressReset();

            // Optional: Reset session count when fully stopped.
            // If you want sessions to persist until app is closed, delete these 2 lines.
            // sessionCount = 0;
            // binding.tvSessionCount.setText("0");
        }
    }

    private void setStatusPill(String text, int dotDrawable) {
        binding.tvStatus.setText(text);
        binding.viewStatusDot.setBackgroundResource(dotDrawable);
    }

    private void setFab(String text, int iconRes) {
        boolean changed = !text.equals(binding.btnStartStop.getText().toString());
        binding.btnStartStop.setText(text);
        binding.btnStartStop.setIconResource(iconRes);
        if (changed) animateFabChange();
    }

    private void showModeBadge(String label, boolean isBreak) {
        if (label.isEmpty()) {
            binding.tvModeBadge.setVisibility(View.GONE);
            return;
        }
        binding.tvModeBadge.setText(label);
        binding.tvModeBadge.setVisibility(View.VISIBLE);
    }

    private void setRingColor(boolean amber) {
        // Simplified for compatibility
        binding.progressCircular.setIndicatorColor(amber ? 0xFFFFB300 : 0xFF00E5A0);
        binding.progressGlow.setIndicatorColor(amber ? 0xFFFFB300 : 0xFF00E5A0);
    }

    private void animateProgressReset() {
        ValueAnimator anim = ValueAnimator.ofInt(binding.progressCircular.getProgress(), 0);
        anim.setDuration(600);
        anim.setInterpolator(new FastOutSlowInInterpolator());
        anim.addUpdateListener(va -> {
            int v = (int) va.getAnimatedValue();
            binding.progressCircular.setProgress(v);
            binding.progressGlow.setProgress(v);
        });
        anim.start();
    }

    private void animateFabChange() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(binding.btnStartStop, View.SCALE_X, 0.92f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(binding.btnStartStop, View.SCALE_Y, 0.92f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(280);
        set.setInterpolator(new OvershootInterpolator(2f));
        set.start();
    }

    private void flashRing() {
        ObjectAnimator flash = ObjectAnimator.ofFloat(binding.progressCircular, View.ALPHA, 1f, 0.3f, 1f);
        flash.setDuration(600);
        flash.setRepeatCount(3);
        flash.start();
    }

    private void startPulse() {
        if (pulseAnimator != null && pulseAnimator.isRunning()) return;
        pulseAnimator = ValueAnimator.ofFloat(0.97f, 1.03f);
        pulseAnimator.setDuration(1800);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(va -> {
            float scale = (float) va.getAnimatedValue();
            binding.progressGlow.setScaleX(scale);
            binding.progressGlow.setScaleY(scale);
        });
        pulseAnimator.start();
    }

    private void stopPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            binding.progressGlow.setScaleX(1f);
            binding.progressGlow.setScaleY(1f);
        }
    }

    private void animateEntrance() {
        int delay = 80;
        View[] views = {
                binding.appBarLayout,
                binding.progressGlow,
                binding.progressCircular,
                binding.tvCountdown,
                binding.btnStartStop
        };
        for (int i = 0; i < views.length; i++) {
            if (views[i] == null) continue;
            views[i].setAlpha(0f);
            views[i].setTranslationY(28f);
            views[i].animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay((long) i * delay)
                    .setDuration(500)
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .start();
        }
    }

    private void setupClickListeners() {
        binding.btnStartStop.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            viewModel.toggleTimer();
        });

        // Info Button Click Listener
        binding.btnTipInfo.setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag(InfoBottomSheet.TAG) == null) {
                InfoBottomSheet infoSheet = new InfoBottomSheet();
                infoSheet.show(getSupportFragmentManager(), InfoBottomSheet.TAG);
            }
        });

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {

                // Guard 1: Time-based debounce (1000ms gapping)
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastSettingsClickTime < 1000) {
                    return true; // Ignore rapid double clicks
                }
                lastSettingsClickTime = currentTime;

                // Guard 2: Prevent overlapping instances
                if (getSupportFragmentManager().findFragmentByTag(SettingsBottomSheet.TAG) == null) {
                    SettingsBottomSheet sheet = new SettingsBottomSheet();
                    sheet.show(getSupportFragmentManager(), SettingsBottomSheet.TAG);
                }
                return true;
            }
            return false;
        });
    }

    private void showExactAlarmDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Alarms & Reminders")
                .setMessage("OptiPause needs the Exact Alarm permission to remind you at precise intervals. Please enable it in Settings.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        exactAlarmLauncher.launch(new Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + getPackageName())));
                    }
                })
                .setNegativeButton("Not Now", null)
                .show();
    }

    private void checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            android.app.NotificationManager nm =
                    getSystemService(android.app.NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Wake Screen Permission")
                        .setMessage("Allow OptiPause to wake your screen when a break alarm fires.")
                        .setPositiveButton("Allow", (d, w) -> {
                            Intent intent = new Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Skip", null)
                        .show();
            }
        }
    }

    private String formatTime(long millis) {
        if (millis <= 0) return "00:00";
        long total = millis / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0
                ? String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
                : String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }
}