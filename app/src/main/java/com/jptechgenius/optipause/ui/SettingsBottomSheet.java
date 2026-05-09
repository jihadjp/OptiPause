package com.jptechgenius.optipause.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.jptechgenius.optipause.databinding.BottomSheetSettingsBinding;

/**
 * SettingsBottomSheet
 *
 * Modified to support 30 seconds interval along with minutes.
 */
public class SettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "SettingsBottomSheet";

    // Preset values in seconds: 30s, 1m, 5m, 10m, 15m, 20m, 30m, 45m, 60m
    private static final int[] PRESETS_SECONDS = {30, 60, 300, 600, 900, 1200, 1800, 2700, 3600};

    private static final String ARG_CURRENT_SECONDS = "current_seconds";

    public interface OnIntervalSelectedListener {
        // Return result in minutes (float) or you can change your logic to seconds
        void onIntervalSelected(int seconds);
    }

    private BottomSheetSettingsBinding binding;
    private OnIntervalSelectedListener listener;
    private int currentSeconds = 1200; // Default 20 mins

    public static SettingsBottomSheet newInstance(int currentSeconds) {
        SettingsBottomSheet sheet = new SettingsBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_CURRENT_SECONDS, currentSeconds);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnIntervalSelectedListener(OnIntervalSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentSeconds = getArguments().getInt(ARG_CURRENT_SECONDS, 1200);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupPresetChips();
        setupSeekBar();
        setupApplyButton();
        updateLabel(currentSeconds);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupPresetChips() {
        ChipGroup chipGroup = binding.chipGroupPresets;
        for (int seconds : PRESETS_SECONDS) {
            Chip chip = new Chip(requireContext());
            if (seconds < 60) {
                chip.setText(seconds + " sec");
            } else {
                chip.setText((seconds / 60) + " min");
            }

            chip.setCheckable(true);
            chip.setChecked(seconds == currentSeconds);
            chip.setTag(seconds);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    currentSeconds = (int) chip.getTag();
                    // Sync seekbar: Here 1 unit in seekbar = 30 seconds
                    binding.seekbarInterval.setProgress((currentSeconds / 30) - 1);
                    updateLabel(currentSeconds);
                }
            });
            chipGroup.addView(chip);
        }
    }

    private void setupSeekBar() {
        // Seekbar Range: 1 to 240 (Step of 30s, so max is 120 mins = 7200s / 30 = 240 steps)
        binding.seekbarInterval.setMax(239);
        binding.seekbarInterval.setProgress((currentSeconds / 30) - 1);

        binding.seekbarInterval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentSeconds = (progress + 1) * 30;
                    updateLabel(currentSeconds);
                    uncheckAllChips();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupApplyButton() {
        binding.btnApply.setOnClickListener(v -> {
            if (listener != null) {
                listener.onIntervalSelected(currentSeconds);
            }
            dismiss();
        });
    }

    private void updateLabel(int totalSeconds) {
        if (binding == null) return;
        String label;
        if (totalSeconds < 60) {
            label = totalSeconds + " seconds";
        } else if (totalSeconds < 3600) {
            int m = totalSeconds / 60;
            int s = totalSeconds % 60;
            label = m + " min" + (s > 0 ? " " + s + "s" : "");
        } else {
            int h = totalSeconds / 3600;
            int m = (totalSeconds % 3600) / 60;
            label = h + "h" + (m > 0 ? " " + m + "m" : "");
        }
        binding.tvIntervalValue.setText(label);
    }

    private void uncheckAllChips() {
        if (binding == null) return;
        ChipGroup cg = binding.chipGroupPresets;
        for (int i = 0; i < cg.getChildCount(); i++) {
            View child = cg.getChildAt(i);
            if (child instanceof Chip) ((Chip) child).setChecked(false);
        }
    }
}