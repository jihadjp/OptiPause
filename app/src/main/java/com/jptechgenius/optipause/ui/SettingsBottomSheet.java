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
 * A Material 3 BottomSheetDialogFragment that lets the user configure:
 *  - A custom interval via a SeekBar (1–120 minutes)
 *  - Preset intervals via Chips: 5, 10, 15, 20, 30, 45, 60 minutes
 *
 * The host Activity/Fragment implements {@link OnIntervalSelectedListener}
 * to receive the result.
 */
public class SettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "SettingsBottomSheet";

    // ─── Preset options (minutes) ─────────────────────────────────────────────
    private static final int[] PRESETS = {5, 10, 15, 20, 30, 45, 60};

    // ─── Argument key ─────────────────────────────────────────────────────────
    private static final String ARG_CURRENT_MINUTES = "current_minutes";

    // ─── Callback interface ───────────────────────────────────────────────────
    public interface OnIntervalSelectedListener {
        void onIntervalSelected(int minutes);
    }

    private BottomSheetSettingsBinding binding;
    private OnIntervalSelectedListener listener;
    private int currentMinutes = 20;

    // ─────────────────────────────────────────────────────────────────────────
    // Factory
    // ─────────────────────────────────────────────────────────────────────────

    public static SettingsBottomSheet newInstance(int currentMinutes) {
        SettingsBottomSheet sheet = new SettingsBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_CURRENT_MINUTES, currentMinutes);
        sheet.setArguments(args);
        return sheet;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Listener registration
    // ─────────────────────────────────────────────────────────────────────────

    public void setOnIntervalSelectedListener(OnIntervalSelectedListener listener) {
        this.listener = listener;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentMinutes = getArguments().getInt(ARG_CURRENT_MINUTES, 20);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupPresetChips();
        setupSeekBar();
        setupApplyButton();
        updateLabel(currentMinutes);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────────────

    private void setupPresetChips() {
        ChipGroup chipGroup = binding.chipGroupPresets;
        for (int minutes : PRESETS) {
            Chip chip = new Chip(requireContext());
            chip.setText(minutes + " min");
            chip.setCheckable(true);
            chip.setChecked(minutes == currentMinutes);
            chip.setTag(minutes);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    int selected = (int) chip.getTag();
                    currentMinutes = selected;
                    // Sync seekbar without triggering its listener
                    binding.seekbarInterval.setProgress(selected - 1);
                    updateLabel(selected);
                }
            });
            chipGroup.addView(chip);
        }
    }

    private void setupSeekBar() {
        // Range: 1–120 minutes (SeekBar: 0–119)
        binding.seekbarInterval.setMax(119);
        binding.seekbarInterval.setProgress(currentMinutes - 1);

        binding.seekbarInterval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int minutes = progress + 1;
                    currentMinutes = minutes;
                    updateLabel(minutes);
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
                listener.onIntervalSelected(currentMinutes);
            }
            dismiss();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void updateLabel(int minutes) {
        if (binding == null) return;
        String label;
        if (minutes < 60) {
            label = minutes + " minute" + (minutes == 1 ? "" : "s");
        } else {
            int h = minutes / 60;
            int m = minutes % 60;
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
