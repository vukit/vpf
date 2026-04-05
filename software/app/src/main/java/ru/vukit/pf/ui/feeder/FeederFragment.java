package ru.vukit.pf.ui.feeder;

import android.annotation.SuppressLint;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import ru.vukit.pf.R;
import ru.vukit.pf.databinding.FragmentFeederBinding;

@SuppressLint("MissingPermission")
public class FeederFragment extends Fragment {

    private FeederController controller;
    private FeederModel model;
    private FragmentFeederBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        model = FeederModel.getInstance();
        model.init(this, requireArguments());

        controller = FeederController.getInstance();
        controller.init(this, requireArguments());

        binding = FragmentFeederBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        binding.fabFeederSettingsRefresh.setOnClickListener(view -> {
            if (controller.readSettings()) {
                model.viewState = FeederController.SETTINGS_READ_PROGRESS;
                updateView();
            }
        });

        binding.feederName.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                model.name = String.valueOf(s);
            }
        });

        binding.feeding1Time.setOnClickListener(feedingTime(0));
        binding.feeding2Time.setOnClickListener(feedingTime(1));
        binding.feeding3Time.setOnClickListener(feedingTime(2));

        binding.feeding1Weight.addTextChangedListener(feedingWeight(0));
        binding.feeding2Weight.addTextChangedListener(feedingWeight(1));
        binding.feeding3Weight.addTextChangedListener(feedingWeight(2));

        binding.feederSetScalesZero.setOnCheckedChangeListener((buttonView, isChecked) -> model.setScalesZero = isChecked);

        binding.feederSaveButton.setOnClickListener(view -> {
            if (controller.writeSettings()) {
                model.viewState = FeederController.SETTINGS_WRITE_PROGRESS;
                updateView();
            }
        });

        binding.feederFeedButton.setOnClickListener(view -> {
            if (model.feedWeight != 0 && controller.feed()) {
                model.feedShippedWeight = 0;
                model.viewState = FeederController.FEED_PROGRESS;
                updateView();
            }
        });

        binding.feederFeedWeight.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() != 0) {
                    model.feedWeight = Integer.valueOf(String.valueOf(s));
                }
            }
        });

        binding.feederUpdateFirmwareButton.setOnClickListener(view -> {
            if (controller.updateFirmware()) {
                model.viewState = FeederController.UPDATING_FIRMWARE_PROGRESS;
                updateView();
            }
        });

        updateView();

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (model.viewState == FeederController.SETTINGS_READ_PROGRESS) {
            controller.readSettings();
        }
    }

    @Override
    public void onStop() {
        controller.unsetFragment();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @SuppressLint("DefaultLocale")
    public void updateView() {
        String counter;
        EditText feederName = binding.feederName;
        feederName.setText(model.name);
        CheckBox setScalesZero = binding.feederSetScalesZero;
        setScalesZero.setChecked(model.setScalesZero);
        TextView feederWeight = binding.feederWeight;
        feederWeight.setText(String.valueOf(model.weight));
        TextView feederFeedWeight = binding.feederFeedWeight;
        feederFeedWeight.setText(String.valueOf(model.feedWeight));
        switch (model.viewState) {
            case FeederController.SETTINGS_READ_SUCCESS:
                binding.feederProgressIndicator.setVisibility(View.GONE);
                binding.feederEmpty.setVisibility(View.GONE);
                binding.fabFeederSettingsRefresh.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.VISIBLE);
                TextView feederTemperature = binding.feederTemperature;
                feederTemperature.setText(String.format("%.2f", model.temperature));
                TextView f1time = binding.feeding1Time;
                f1time.setText(String.format("%02d:%02d", model.feedings[0].hour, model.feedings[0].minute));
                TextView f1weight = binding.feeding1Weight;
                f1weight.setText(String.valueOf(model.feedings[0].weight));
                TextView f2time = binding.feeding2Time;
                f2time.setText(String.format("%02d:%02d", model.feedings[1].hour, model.feedings[1].minute));
                TextView f2weight = binding.feeding2Weight;
                f2weight.setText(String.valueOf(model.feedings[1].weight));
                TextView f3time = binding.feeding3Time;
                f3time.setText(String.format("%02d:%02d", model.feedings[2].hour, model.feedings[2].minute));
                TextView f3weight = binding.feeding3Weight;
                f3weight.setText(String.valueOf(model.feedings[2].weight));
                binding.feederUpdateFirmwareLayout.setVisibility(View.GONE);
                if (model.doNeedUpdateFirmware()) {
                    binding.feederUpdateFirmwareLayout.setVisibility(View.VISIBLE);
                    TextView currentFirmware = binding.feederCurrentFirmware;
                    currentFirmware.setText(model.feederFirmware);
                    TextView availableFirmware = binding.feederAvailableFirmware;
                    availableFirmware.setText(model.availableFirmware);
                }
                break;
            case FeederController.SETTINGS_READ_FAIL:
                binding.feederProgressIndicator.setVisibility(View.GONE);
                binding.feederEmpty.setText(R.string.feeder_settings_reading_fail);
                binding.feederEmpty.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.GONE);
                binding.fabFeederSettingsRefresh.setVisibility(View.VISIBLE);
                break;
            case FeederController.SETTINGS_READ_FEEDER_BUSY:
                binding.feederProgressIndicator.setVisibility(View.GONE);
                binding.feederEmpty.setText(R.string.feeder_settings_reading_fail_feeder_busy);
                binding.feederEmpty.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.GONE);
                binding.fabFeederSettingsRefresh.setVisibility(View.VISIBLE);
                break;
            case FeederController.SETTINGS_READ_PROGRESS:
                binding.feederProgressIndicator.setVisibility(View.VISIBLE);
                binding.fabFeederSettingsRefresh.setVisibility(View.GONE);
                binding.feederEmpty.setText(R.string.feeder_settings_reading);
                binding.feederEmpty.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.GONE);
                break;
            case FeederController.SETTINGS_WRITE_PROGRESS:
                binding.feederProgressIndicator.setVisibility(View.VISIBLE);
                binding.fabFeederSettingsRefresh.setVisibility(View.GONE);
                binding.feederEmpty.setText(R.string.feeder_settings_writing);
                binding.feederEmpty.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.GONE);
                break;
            case FeederController.SETTINGS_WRITE_SUCCESS:
                binding.feederProgressIndicator.setVisibility(View.GONE);
                binding.feederEmpty.setVisibility(View.GONE);
                binding.fabFeederSettingsRefresh.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.VISIBLE);
                model.viewState = FeederController.SETTINGS_READ_SUCCESS;
                Toast.makeText(requireContext(), getString(R.string.feeder_settings_writing_success), Toast.LENGTH_LONG).show();
                break;
            case FeederController.SETTINGS_WRITE_FAIL:
                binding.feederProgressIndicator.setVisibility(View.GONE);
                binding.feederEmpty.setVisibility(View.GONE);
                binding.fabFeederSettingsRefresh.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.VISIBLE);
                model.viewState = FeederController.SETTINGS_READ_SUCCESS;
                Toast.makeText(requireContext(), getString(R.string.feeder_settings_writing_fail), Toast.LENGTH_LONG).show();
                break;
            case FeederController.SETTINGS_WRITE_FEEDER_BUSY:
                binding.feederProgressIndicator.setVisibility(View.GONE);
                binding.feederEmpty.setVisibility(View.GONE);
                binding.fabFeederSettingsRefresh.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.VISIBLE);
                model.viewState = FeederController.SETTINGS_READ_SUCCESS;
                Toast.makeText(requireContext(), getString(R.string.feeder_settings_writing_fail_feeder_busy), Toast.LENGTH_LONG).show();
                break;
            case FeederController.FEED_PROGRESS:
                if (model.feedShippedWeight == 0) {
                    binding.feederProgressIndicator.setVisibility(View.VISIBLE);
                    binding.fabFeederSettingsRefresh.setVisibility(View.GONE);
                    binding.feederEmpty.setVisibility(View.VISIBLE);
                    binding.feederMainScreen.setVisibility(View.GONE);
                }
                counter = getString(R.string.feeder_sending_feeding_command) + " " + model.feedShippedWeight;
                binding.feederEmpty.setText(counter);
                break;
            case FeederController.FEED_SUCCESS:
                binding.feederProgressIndicator.setVisibility(View.GONE);
                binding.feederEmpty.setVisibility(View.GONE);
                binding.fabFeederSettingsRefresh.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.VISIBLE);
                model.viewState = FeederController.SETTINGS_READ_SUCCESS;
                Toast.makeText(requireContext(), getString(R.string.feeder_sending_feeding_command_success), Toast.LENGTH_LONG).show();
                break;
            case FeederController.FEED_FAIL:
                binding.feederProgressIndicator.setVisibility(View.GONE);
                binding.feederEmpty.setVisibility(View.GONE);
                binding.fabFeederSettingsRefresh.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.VISIBLE);
                model.viewState = FeederController.SETTINGS_READ_SUCCESS;
                Toast.makeText(requireContext(), getString(R.string.feeder_sending_feeding_command_fail), Toast.LENGTH_LONG).show();
                break;
            case FeederController.FEED_FEEDER_BUSY:
                binding.feederProgressIndicator.setVisibility(View.GONE);
                binding.feederEmpty.setVisibility(View.GONE);
                binding.fabFeederSettingsRefresh.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.VISIBLE);
                model.viewState = FeederController.SETTINGS_READ_SUCCESS;
                Toast.makeText(requireContext(), getString(R.string.feeder_sending_feeding_command_fail_feeder_busy), Toast.LENGTH_LONG).show();
                break;
            case FeederController.UPDATING_FIRMWARE_PROGRESS:
                if (model.firmwarePacketNumber == 0) {
                    binding.feederProgressIndicator.setVisibility(View.VISIBLE);
                    binding.fabFeederSettingsRefresh.setVisibility(View.GONE);
                    binding.feederEmpty.setVisibility(View.VISIBLE);
                    binding.feederMainScreen.setVisibility(View.GONE);
                }
                counter = getString(R.string.feeder_updating_firmware_progress) + " " + model.firmwarePacketNumber + "/" + model.firmwareAllPackets;
                binding.feederEmpty.setText(counter);
                break;
            case FeederController.UPDATING_FIRMWARE_SUCCESS:
                binding.feederProgressIndicator.setVisibility(View.GONE);
                binding.feederEmpty.setVisibility(View.GONE);
                binding.fabFeederSettingsRefresh.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.VISIBLE);
                binding.feederUpdateFirmwareLayout.setVisibility(View.GONE);
                model.viewState = FeederController.SETTINGS_READ_SUCCESS;
                Toast.makeText(requireContext(), getString(R.string.feeder_updating_firmware_success), Toast.LENGTH_LONG).show();
                break;
            case FeederController.UPDATING_FIRMWARE_FAIL:
                binding.feederProgressIndicator.setVisibility(View.GONE);
                binding.feederEmpty.setVisibility(View.GONE);
                binding.fabFeederSettingsRefresh.setVisibility(View.VISIBLE);
                binding.feederMainScreen.setVisibility(View.VISIBLE);
                model.viewState = FeederController.SETTINGS_READ_SUCCESS;
                Toast.makeText(requireContext(), getString(R.string.feeder_updating_firmware_fail), Toast.LENGTH_LONG).show();
                break;
        }
    }

    private View.OnClickListener feedingTime(int number) {
        return v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(requireContext(),
                    (view, hourOfDay, minute) -> {
                        model.feedings[number].hour = hourOfDay;
                        model.feedings[number].minute = minute;
                        updateView();
                    }, model.feedings[number].hour, model.feedings[number].minute, true);
            timePickerDialog.show();
        };
    }

    private TextWatcher feedingWeight(int number) {
        return new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() != 0) {
                    model.feedings[number].weight = Integer.valueOf(String.valueOf(s));
                }
            }
        };
    }
}