package ru.vukit.pf.ui.feeder;

import android.content.res.AssetManager;
import android.os.Bundle;

import java.io.IOException;
import java.util.Objects;

public class FeederModel {
    private static FeederModel INSTANCE = null;

    public int viewState;

    public static final int FEEDING_COUNT = 3;

    public String name;

    public String address;

    public Integer weight;

    public Double temperature;

    public Boolean setScalesZero = false;

    public Integer feedWeight;

    public Integer feedShippedWeight;

    public String feederFirmware = "unknown";

    public String availableFirmware = "unknown";

    public int firmwarePacketNumber = 0;
    public int firmwareAllPackets = 0;

    static class Feeding {
        public Integer hour;
        public Integer minute;
        public Integer weight;
    }

    public final Feeding[] feedings = new Feeding[FEEDING_COUNT];

    public static synchronized FeederModel getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FeederModel();
        }
        return (INSTANCE);
    }

    public void init(FeederFragment fragment, Bundle bundle) {
        if (!Objects.equals(bundle.getString("address"), this.address) || bundle.getBoolean("wasThereNewScan")) {
            this.viewState = FeederController.SETTINGS_READ_PROGRESS;
            this.name = bundle.getString("name");
            this.address = bundle.getString("address");
            this.weight = null;
            this.temperature = null;
            this.setScalesZero = false;
            this.feedWeight = 0;
            for (int i = 0; i < FEEDING_COUNT; i++) {
                this.feedings[i] = new Feeding();
            }
            AssetManager assetManager = fragment.requireContext().getAssets();
            try {
                String[] files = assetManager.list("firmware");
                for (int i = 0; i < Objects.requireNonNull(files).length; i++) {
                    if (files[i].endsWith("bin")) {
                        availableFirmware = files[i].substring(0, files[i].length() - 4);
                        break;
                    }
                }
            } catch (IOException e) {
                this.availableFirmware = "unknown";
            }
            this.firmwarePacketNumber = 0;
            this.firmwareAllPackets = 0;
        }
    }

    public boolean doNeedUpdateFirmware() {
        String[] feederFirmwareVersions = this.feederFirmware.split("\\.");
        String[] availableFirmwareVersions = this.availableFirmware.split("\\.");
        try {
            if (Integer.parseInt(feederFirmwareVersions[0]) < Integer.parseInt(availableFirmwareVersions[0])) {
                return true;
            }
            if (Integer.parseInt(feederFirmwareVersions[0]) == Integer.parseInt(availableFirmwareVersions[0])) {
                if (Integer.parseInt(feederFirmwareVersions[1]) < Integer.parseInt(availableFirmwareVersions[1])) {
                    return true;
                }
                if (Integer.parseInt(feederFirmwareVersions[1]) == Integer.parseInt(availableFirmwareVersions[1])) {
                    if (Integer.parseInt(feederFirmwareVersions[2]) < Integer.parseInt(availableFirmwareVersions[2])) {
                        return true;
                    }
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }
}
