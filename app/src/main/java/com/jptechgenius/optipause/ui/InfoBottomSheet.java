package com.jptechgenius.optipause.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.jptechgenius.optipause.databinding.BottomSheetInfoBinding;

public class InfoBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "InfoBottomSheet";
    private BottomSheetInfoBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetInfoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // "Got It" Button
        binding.btnGotIt.setOnClickListener(v -> dismiss());

        // "See Profile" Click (Opens Developer Profile)
        binding.tvSeeProfile.setOnClickListener(v -> {
            // Optional: Dismiss current info sheet when opening profile
            // dismiss();

            if (getParentFragmentManager().findFragmentByTag(DeveloperBottomSheet.TAG) == null) {
                DeveloperBottomSheet devSheet = new DeveloperBottomSheet();
                devSheet.show(getParentFragmentManager(), DeveloperBottomSheet.TAG);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}