package com.jptechgenius.optipause.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jptechgenius.optipause.databinding.BottomSheetSettingsBinding;
import com.jptechgenius.optipause.repository.TimerRepository;
import com.jptechgenius.optipause.viewmodel.TimerViewModel;

public class SettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "SettingsBottomSheet";

    private BottomSheetSettingsBinding binding;
    private TimerRepository repository;
    private TimerViewModel viewModel;

    private String tempWorkTone = "";
    private String tempBreakTone = "";
    private boolean isPickingWorkTone = true;

    // 1. Launcher for File Manager (Custom Audio)
    private final ActivityResultLauncher<String[]> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    handlePickedUri(uri, true);
                }
            }
    );

    // 2. Launcher for System Ringtone Picker
    private final ActivityResultLauncher<Intent> ringtonePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (uri != null) {
                        handlePickedUri(uri, false);
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSettingsBinding.inflate(inflater, container, false);
        repository = new TimerRepository(requireContext());
        viewModel = new ViewModelProvider(requireActivity()).get(TimerViewModel.class);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadCurrentSettings();
        setupClickListeners();
    }

    private void loadCurrentSettings() {
        long workMillis = repository.getWorkMillis();
        long breakMillis = repository.getBreakMillis();

        binding.etWorkMin.setText(String.valueOf(workMillis / 60000));
        binding.etWorkSec.setText(String.valueOf((workMillis % 60000) / 1000));

        binding.etBreakMin.setText(String.valueOf(breakMillis / 60000));
        binding.etBreakSec.setText(String.valueOf((breakMillis % 60000) / 1000));

        tempWorkTone = repository.getWorkTone();
        tempBreakTone = repository.getBreakTone();

        binding.tvWorkToneName.setText(getToneDisplayName(tempWorkTone));
        binding.tvBreakToneName.setText(getToneDisplayName(tempBreakTone));
    }

    private void setupClickListeners() {
        binding.btnPickWorkTone.setOnClickListener(v -> showToneSelectionDialog(true));
        binding.btnPickBreakTone.setOnClickListener(v -> showToneSelectionDialog(false));

        binding.btnApply.setOnClickListener(v -> {
            long workM = calculateMillis(binding.etWorkMin.getText().toString(), binding.etWorkSec.getText().toString());
            long breakM = calculateMillis(binding.etBreakMin.getText().toString(), binding.etBreakSec.getText().toString());

            if (workM < 10000 || breakM < 10000) {
                Toast.makeText(getContext(), "Minimum 10 seconds required!", Toast.LENGTH_SHORT).show();
                return;
            }

            repository.saveWorkMillis(workM);
            repository.saveBreakMillis(breakM);
            repository.saveWorkTone(tempWorkTone);
            repository.saveBreakTone(tempBreakTone);

            viewModel.refreshStateFromSettings();
            dismiss();
        });
    }

    // popup dialog to choose between File Manager or Ringtone
    private void showToneSelectionDialog(boolean forWork) {
        isPickingWorkTone = forWork;
        String[] options = {"System Ringtone", "File Manager (Custom Audio)"};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Tone Source")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // System Ringtone Picker
                        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, forWork ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION);
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                        ringtonePickerLauncher.launch(intent);
                    } else {
                        // File Manager
                        filePickerLauncher.launch(new String[]{"audio/*"});
                    }
                })
                .show();
    }

    private void handlePickedUri(Uri uri, boolean isFromFileManager) {
        if (isFromFileManager) {
            try {
                requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String uriStr = uri.toString();
        if (isPickingWorkTone) {
            tempWorkTone = uriStr;
            binding.tvWorkToneName.setText(getToneDisplayName(uriStr));
        } else {
            tempBreakTone = uriStr;
            binding.tvBreakToneName.setText(getToneDisplayName(uriStr));
        }
    }

    private String getToneDisplayName(String uriStr) {
        if (TextUtils.isEmpty(uriStr)) return "Default Tone";
        Uri uri = Uri.parse(uriStr);

        // Try extracted file name first
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) name = cursor.getString(index);
                }
            }
        }

        if (name == null) {
            try {
                Ringtone r = RingtoneManager.getRingtone(getContext(), uri);
                name = r.getTitle(getContext());
            } catch (Exception e) {
                name = "Custom Tone";
            }
        }
        return name;
    }

    private long calculateMillis(String m, String s) {
        long min = TextUtils.isEmpty(m) ? 0 : Long.parseLong(m);
        long sec = TextUtils.isEmpty(s) ? 0 : Long.parseLong(s);
        return (min * 60 + sec) * 1000L;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}