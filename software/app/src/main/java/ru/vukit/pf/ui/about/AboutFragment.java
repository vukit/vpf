package ru.vukit.pf.ui.about;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import ru.vukit.pf.R;
import ru.vukit.pf.databinding.FragmentAboutBinding;

public class AboutFragment extends Fragment {

    private FragmentAboutBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentAboutBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        String version;
        PackageInfo packageinfo;
        binding.instructionUrl.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.action_instruction_url)))));
        binding.privacyPolicyUrl.setOnClickListener((v) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.action_privacy_policy_url)))));
        binding.licenseUrl.setOnClickListener((v) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.action_license_url)))));
        binding.allProjectsUrl.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.action_all_projects_url)))));
        try {
            packageinfo = requireActivity().getPackageManager().getPackageInfo(requireActivity().getPackageName(), 0);
            version = getString(R.string.app_version) + " " + packageinfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            version = "";
        }
        binding.appVersion.setText(version);
        binding.vendorName.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.action_vendor_url)))));

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}