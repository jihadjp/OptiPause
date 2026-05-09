package com.jptechgenius.optipause.ui;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.jptechgenius.optipause.R;
import com.jptechgenius.optipause.databinding.BottomSheetDeveloperBinding;

public class DeveloperBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "DeveloperBottomSheet";
    private BottomSheetDeveloperBinding binding;

    // ── Developer Social Media & Contact Info ──
    private static final String URL_FACEBOOK = "https://www.facebook.com/jihadjp100";
    private static final String URL_YOUTUBE = "https://www.youtube.com/@jihadjp";
    private static final String URL_INSTAGRAM = "https://www.instagram.com/jihadjp100";
    private static final String URL_LINKEDIN = "https://www.linkedin.com/in/your_jihadjp";
    private static final String URL_GITHUB = "https://github.com/jihadjp";
    private static final String URL_WEBSITE = "https://jihadjp.com";
    private static final String EMAIL = "aactech.info@gmail.com";
    private static final String PHONE = "+8801602222587";

    // Buy me a coffee Link
    private static final String URL_BUY_COFFEE = "https://www.buymeacoffee.com/jihadjp";


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetDeveloperBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupClickListeners();
    }

    private void setupClickListeners() {
        // Close Button
        binding.btnClose.setOnClickListener(v -> dismiss());

        // Contact Actions
        binding.btnEmail.setOnClickListener(v -> sendEmail());
        binding.btnWhatsapp.setOnClickListener(v -> openWhatsApp());
        binding.btnWebsite.setOnClickListener(v -> openUrl(URL_WEBSITE));

        // Social Media Links
        binding.btnFacebook.setOnClickListener(v -> openUrl(URL_FACEBOOK));
        binding.btnYoutube.setOnClickListener(v -> openUrl(URL_YOUTUBE));
        binding.btnInstagram.setOnClickListener(v -> openUrl(URL_INSTAGRAM));
        binding.btnLinkedin.setOnClickListener(v -> openUrl(URL_LINKEDIN));
        binding.btnGithub.setOnClickListener(v -> openUrl(URL_GITHUB));

        binding.imageDeveloper.setOnClickListener(v -> showDeveloperImageDialog());
        binding.btnBuyCoffee.setOnClickListener(v -> openUrl(URL_BUY_COFFEE));
    }

    private void showDeveloperImageDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_developer_image);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialog.show();
    }

    private void sendEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + EMAIL));
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " - Feedback");
        try {
            startActivity(Intent.createChooser(intent, "Send Email"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "No email app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWhatsApp() {
        String phoneNumber = PHONE.replace("+", "").replace(" ", "");
        String url = "https://wa.me/" + phoneNumber;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            // Fallback to browser if WhatsApp is not installed
            openUrl(url);
        }
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Cannot open link.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}