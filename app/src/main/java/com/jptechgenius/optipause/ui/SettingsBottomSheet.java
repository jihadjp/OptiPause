package com.jptechgenius.optipause.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.jptechgenius.optipause.databinding.BottomSheetSettingsBinding;
import com.jptechgenius.optipause.repository.TimerRepository;
import com.jptechgenius.optipause.viewmodel.TimerViewModel;

/**
 * SettingsBottomSheet
 *
 * Allows users to manually configure exact Work and Break intervals
 * by inputting Minutes and Seconds.
 */
public class SettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "SettingsBottomSheet";

    private BottomSheetSettingsBinding binding;
    private TimerRepository repository;
    private TimerViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSettingsBinding.inflate(inflater, container, false);
        repository = new TimerRepository(requireContext());

        // We get the Activity's ViewModel so we can update the dashboard instantly
        // after saving new settings.
        viewModel = new ViewModelProvider(requireActivity()).get(TimerViewModel.class);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loadCurrentSettings();
        setupApplyButton();
    }

    private void loadCurrentSettings() {
        long workMillis = repository.getWorkMillis();
        long breakMillis = repository.getBreakMillis();

        // Convert Milliseconds to Minutes & Seconds
        int workMin = (int) (workMillis / 60000);
        int workSec = (int) ((workMillis % 60000) / 1000);

        int breakMin = (int) (breakMillis / 60000);
        int breakSec = (int) ((breakMillis % 60000) / 1000);

        // Populate Input Fields
        binding.etWorkMin.setText(String.valueOf(workMin));
        binding.etWorkSec.setText(String.valueOf(workSec));
        binding.etBreakMin.setText(String.valueOf(breakMin));
        binding.etBreakSec.setText(String.valueOf(breakSec));
    }

    private void setupApplyButton() {
        binding.btnApply.setOnClickListener(v -> {

            // Calculate total milliseconds from User Inputs
            long newWorkMillis = calculateMillis(
                    binding.etWorkMin.getText().toString().trim(),
                    binding.etWorkSec.getText().toString().trim()
            );

            long newBreakMillis = calculateMillis(
                    binding.etBreakMin.getText().toString().trim(),
                    binding.etBreakSec.getText().toString().trim()
            );

            // Validation: Ensure the interval is at least 10 seconds
            if (newWorkMillis < 10000) {
                Toast.makeText(requireContext(), "Work duration must be at least 10 seconds!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newBreakMillis < 10000) {
                Toast.makeText(requireContext(), "Break duration must be at least 10 seconds!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save to Repository
            repository.saveWorkMillis(newWorkMillis);
            repository.saveBreakMillis(newBreakMillis);

            // Notify ViewModel to update Dashboard UI with new interval
            viewModel.refreshStateFromSettings();

            // Close BottomSheet
            dismiss();
        });
    }

    /**
     * Helper to convert string minutes and seconds into total milliseconds safely.
     */
    private long calculateMillis(String minStr, String secStr) {
        long minutes = 0;
        long seconds = 0;

        if (!TextUtils.isEmpty(minStr)) {
            minutes = Long.parseLong(minStr);
        }
        if (!TextUtils.isEmpty(secStr)) {
            seconds = Long.parseLong(secStr);
        }

        return (minutes * 60 + seconds) * 1000L;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevent memory leaks
    }
}