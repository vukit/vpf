package ru.vukit.pf.ui.feeder;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class FeederController {
    private static final String TAG = "FeederController";
    private static FeederController INSTANCE = null;
    FeederFragment fragment;
    FeederModel model;
    ru.vukit.pf.database.Driver dbDriver;
    ru.vukit.pf.bluetooth.Driver btDriver;
    private static final int FEEDER_BLE_MTU_SIZE = 256;
    private final UUID feederServiceUUID = UUID.fromString("fa1aee3b-79bb-4f8f-b9e6-d083cf934340");
    private final UUID otaControlUUID = UUID.fromString("fa1aee3b-79bb-4f8f-b9e6-d083cf934341");
    private final UUID otaDataUUID = UUID.fromString("fa1aee3b-79bb-4f8f-b9e6-d083cf934342");
    private final UUID stateGattCharacteristicUUID = UUID.fromString("fa1aee3b-79bb-4f8f-b9e6-d083cf934343");
    private final UUID feedCommandGattCharacteristicUUID = UUID.fromString("fa1aee3b-79bb-4f8f-b9e6-d083cf934344");
    private final UUID CCC_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int FEEDER_BUSY = 0x13;
    public static final int SETTINGS_READ_PROGRESS = 0;
    public static final int SETTINGS_READ_SUCCESS = 1;
    public static final int SETTINGS_READ_FAIL = 2;
    public static final int SETTINGS_READ_FEEDER_BUSY = 3;
    public static final int SETTINGS_WRITE_PROGRESS = 4;
    public static final int SETTINGS_WRITE_SUCCESS = 5;
    public static final int SETTINGS_WRITE_FAIL = 6;
    public static final int SETTINGS_WRITE_FEEDER_BUSY = 7;
    public static final int FEED_PROGRESS = 8;
    public static final int FEED_SUCCESS = 9;
    public static final int FEED_FAIL = 10;
    public static final int FEED_FEEDER_BUSY = 11;
    public static final int UPDATING_FIRMWARE_PROGRESS = 12;
    public static final int UPDATING_FIRMWARE_SUCCESS = 13;
    public static final int UPDATING_FIRMWARE_FAIL = 14;
    private static final int FEED_COMMAND_TIMEOUT = 15000;
    boolean isManualFeeding = false;
    private static final int UPDATING_FIRMWARE_TIMEOUT = 300000;
    private static final byte[] SVR_CHR_OTA_CONTROL_REQUEST = {(byte) 0x01};
    private static final byte[] SVR_CHR_OTA_CONTROL_REQUEST_ACK = {(byte) 0x02};
    private static final byte[] SVR_CHR_OTA_CONTROL_REQUEST_NAK = {(byte) 0x03};
    private static final byte[] SVR_CHR_OTA_CONTROL_DONE = {(byte) 0x04};
    private static final byte[] SVR_CHR_OTA_CONTROL_DONE_ACK = {(byte) 0x05};
    private static final byte[] SVR_CHR_OTA_CONTROL_DONE_NAK = {(byte) 0x06};

    public static synchronized FeederController getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FeederController();
        }
        return (INSTANCE);
    }

    public void init(FeederFragment fragment, Bundle bundle) {
        this.fragment = fragment;
        dbDriver = ru.vukit.pf.database.Driver.getInstance(null);
        btDriver = ru.vukit.pf.bluetooth.Driver.getInstance(null);
        model = FeederModel.getInstance();
        if (bundle.getBoolean("wasThereNewScan")) {
            model.viewState = SETTINGS_READ_PROGRESS;
            bundle.putBoolean("wasThereNewScan", false);
        }
    }

    public void unsetFragment() {
        fragment = null;
    }

    public boolean updateFirmware() {
        if (btDriver.isGattClientBusy()) {
            return false;
        }

        Handler handler = new Handler();
        Runnable runnable = () -> {
            if (model.viewState == UPDATING_FIRMWARE_PROGRESS) {
                btDriver.stopGattClient();
                failUpdatingFirmware();
                Log.d(TAG, "updating firmware: timeout");
            }
        };
        handler.postDelayed(runnable, UPDATING_FIRMWARE_TIMEOUT);

        btDriver.startGattClient(model.address, new BluetoothGattCallback() {
            BluetoothGattService gattService;
            BluetoothGattCharacteristic otaControl;
            BluetoothGattCharacteristic otaData;
            InputStream firmwareStream;
            static final int PACKET_SIZE = FEEDER_BLE_MTU_SIZE - 3;
            final byte[] packet = new byte[PACKET_SIZE];
            boolean updating = false;

            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    gatt.requestMtu(FEEDER_BLE_MTU_SIZE);
                } else {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failUpdatingFirmware();
                    Log.d(TAG, "updating firmware: onConnectionStateChange status: " + status);
                }
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices();
                } else {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failUpdatingFirmware();
                    Log.d(TAG, "updating firmware: onMtuChanged: " + status);
                }
            }

            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gattService = gatt.getService(feederServiceUUID);
                    otaControl = gattService.getCharacteristic(otaControlUUID);
                    otaData = gattService.getCharacteristic(otaDataUUID);
                    gatt.setCharacteristicNotification(otaControl, true);
                    final BluetoothGattDescriptor descriptor = otaControl.getDescriptor(CCC_DESCRIPTOR_UUID);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                } else {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failUpdatingFirmware();
                    Log.d(TAG, "updating firmware: onServicesDiscovered status: " + status);
                }
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
                UUID uuid = descriptor.getCharacteristic().getUuid();
                if (uuid.equals(otaControlUUID)) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        otaControl.setValue(SVR_CHR_OTA_CONTROL_REQUEST);
                        gatt.writeCharacteristic(otaControl);
                    } else {
                        btDriver.stopGattClient();
                        handler.removeCallbacks(runnable);
                        failUpdatingFirmware();
                        Log.d(TAG, "updating firmware: onDescriptorWrite status: " + status);
                    }
                }
            }

            @Override
            public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
                UUID uuid = characteristic.getUuid();
                byte[] value = characteristic.getValue();
                updating = false;
                if (uuid.equals(otaControlUUID)) {
                    if (Arrays.equals(value, SVR_CHR_OTA_CONTROL_REQUEST_ACK)) {
                        Log.d(TAG, "updating firmware: OTA request acknowledged");
                        updating = true;
                        return;
                    }
                    if (Arrays.equals(value, SVR_CHR_OTA_CONTROL_REQUEST_NAK)) {
                        Log.d(TAG, "updating firmware: OTA request NOT acknowledged");
                        btDriver.stopGattClient();
                        handler.removeCallbacks(runnable);
                        failUpdatingFirmware();
                        return;
                    }
                    if (Arrays.equals(value, SVR_CHR_OTA_CONTROL_DONE_ACK)) {
                        Log.d(TAG, "updating firmware: OTA done acknowledged");
                        btDriver.stopGattClient();
                        handler.removeCallbacks(runnable);
                        model.viewState = UPDATING_FIRMWARE_SUCCESS;
                        model.feederFirmware = model.availableFirmware;
                        if (fragment != null) {
                            fragment.requireActivity().runOnUiThread(() -> fragment.updateView());
                        }
                        return;
                    }
                    if (Arrays.equals(value, SVR_CHR_OTA_CONTROL_DONE_NAK)) {
                        Log.d(TAG, "updating firmware: OTA done NOT acknowledged.");
                        btDriver.stopGattClient();
                        handler.removeCallbacks(runnable);
                        failUpdatingFirmware();
                    }
                }
            }

            /** */
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failUpdatingFirmware();
                    Log.d(TAG, "updating firmware: onCharacteristicWrite status: " + status);
                    return;
                }
                UUID uuid = characteristic.getUuid();
                if (uuid.equals(otaControlUUID) && updating) {
                    try {
                        firmwareStream = fragment.requireContext().getResources().getAssets().open("firmware/" + model.availableFirmware + ".bin");
                        model.firmwarePacketNumber = 0;
                        model.firmwareAllPackets = firmwareStream.available() / PACKET_SIZE;
                        if (model.firmwareAllPackets * PACKET_SIZE != firmwareStream.available()) {
                            model.firmwareAllPackets++;
                        }
                    } catch (IOException e) {
                        btDriver.stopGattClient();
                        handler.removeCallbacks(runnable);
                        failUpdatingFirmware();
                        Log.d(TAG, "updating firmware: send firmware exception: " + e.getMessage());
                    }
                }
                if (updating) {
                    try {
                        int readBytes = firmwareStream.read(packet, 0, PACKET_SIZE);
                        if (readBytes == -1) {
                            updating = false;
                            firmwareStream.close();
                            otaControl.setValue(SVR_CHR_OTA_CONTROL_DONE);
                            gatt.writeCharacteristic(otaControl);
                            return;
                        }
                        if (readBytes < PACKET_SIZE) {
                            for (int i = readBytes; i < PACKET_SIZE; i++) {
                                packet[i] = 0x00;
                            }
                        }
                        otaData.setValue(packet);
                        otaData.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        gatt.writeCharacteristic(otaData);
                        model.firmwarePacketNumber++;
                        if (model.firmwarePacketNumber % 10 == 0 && fragment != null) {
                            fragment.requireActivity().runOnUiThread(() -> fragment.updateView());
                        }
                        Log.d(TAG, "updating firmware: send packet " + model.firmwarePacketNumber + "/" + model.firmwareAllPackets);
                    } catch (IOException e) {
                        btDriver.stopGattClient();
                        handler.removeCallbacks(runnable);
                        failUpdatingFirmware();
                        Log.d(TAG, "updating firmware: onCharacteristicWrite exception: " + e.getMessage());
                    }
                }
            }
        });
        return true;
    }

    public boolean feed() {
        if (btDriver.isGattClientBusy()) {
            return false;
        }

        Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (model.viewState == FEED_PROGRESS) {
                    if (!isManualFeeding) {
                        btDriver.stopGattClient();
                        failFeed();
                        Log.d(TAG, "feed: timeout");
                        return;
                    }
                    isManualFeeding = false;
                    handler.postDelayed(this, FEED_COMMAND_TIMEOUT);
                }
            }
        };
        handler.postDelayed(runnable, FEED_COMMAND_TIMEOUT);

        btDriver.startGattClient(model.address, new BluetoothGattCallback() {
            private static final int COMMAND_ERR_FEEDER_BUSY = 0x03;
            private static final int COMMAND_FEEDING_PROGRESS = 0x04;
            private static final int COMMAND_FEEDING_STOP = 0x05;
            BluetoothGattService gattService;
            BluetoothGattCharacteristic feedCommand;

            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    gatt.requestMtu(FEEDER_BLE_MTU_SIZE);
                } else {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failFeed();
                    Log.d(TAG, "feed: connection state change status: " + status);
                }
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices();
                } else {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failFeed();
                    Log.d(TAG, "feed: mtu changed status: " + status);
                }
            }

            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gattService = gatt.getService(feederServiceUUID);
                    feedCommand = gattService.getCharacteristic(feedCommandGattCharacteristicUUID);
                    gatt.setCharacteristicNotification(feedCommand, true);
                    final BluetoothGattDescriptor descriptor = feedCommand.getDescriptor(CCC_DESCRIPTOR_UUID);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                } else {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failFeed();
                    Log.d(TAG, "feed: services discovered status: " + status);
                }
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
                UUID uuid = descriptor.getCharacteristic().getUuid();
                if (uuid.equals(feedCommandGattCharacteristicUUID)) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        feedCommand.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        feedCommand.setValue(model.feedWeight, BluetoothGattCharacteristic.FORMAT_SINT32, 0);
                        gatt.writeCharacteristic(feedCommand);
                    } else {
                        btDriver.stopGattClient();
                        handler.removeCallbacks(runnable);
                        failUpdatingFirmware();
                        Log.d(TAG, "feed: onDescriptorWrite status: " + status);
                    }
                }
            }

            @Override
            public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
                UUID uuid = characteristic.getUuid();
                if (uuid.equals(feedCommandGattCharacteristicUUID)) {
                    isManualFeeding = true;
                    byte[] value = characteristic.getValue();
                    byte code = value[0];
                    if (code == COMMAND_FEEDING_PROGRESS || code == COMMAND_FEEDING_STOP) {
                        model.feedShippedWeight = 0;
                        for (int i = value.length - 1; i > 0; i--) {
                            model.feedShippedWeight = (model.feedShippedWeight << 8) + (value[i] & 0xFF);
                        }
                    }
                    if (code == COMMAND_ERR_FEEDER_BUSY) {
                        model.viewState = FEED_FEEDER_BUSY;
                        btDriver.stopGattClient();
                        handler.removeCallbacks(runnable);
                    }
                    if (code == COMMAND_FEEDING_STOP && model.feedShippedWeight >= model.feedWeight) {
                        model.viewState = FEED_SUCCESS;
                    }
                    if (code == COMMAND_FEEDING_STOP && model.feedShippedWeight < model.feedWeight) {
                        model.viewState = FEED_FAIL;
                    }
                    if (code == COMMAND_FEEDING_STOP ) {
                        model.weight -= model.feedShippedWeight;
                        btDriver.stopGattClient();
                        handler.removeCallbacks(runnable);
                    }
                    if (fragment != null) {
                        fragment.requireActivity().runOnUiThread(() -> fragment.updateView());
                    }
                    Log.d(TAG, "feed: onCharacteristicChanged weight: " + model.feedShippedWeight);
                }
            }
        });
        return true;
    }

    public boolean writeSettings() {
        if (btDriver.isGattClientBusy()) {
            return false;
        }

        HashMap<String, String> dbFeeder = dbDriver.selectFeeder(model.address);
        if (dbFeeder != null) {
            dbDriver.updateFeeder(dbFeeder.get("id"), model.address, model.name);
        }

        Handler handler = new Handler();
        Runnable runnable = () -> {
            if (model.viewState == SETTINGS_WRITE_PROGRESS) {
                btDriver.stopGattClient();
                failWriteSettings();
                Log.d(TAG, "write settings: timeout");
            }
        };
        handler.postDelayed(runnable, ru.vukit.pf.bluetooth.Driver.BLE_OPERATION_DURATION);

        btDriver.startGattClient(model.address, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    gatt.requestMtu(FEEDER_BLE_MTU_SIZE);
                } else {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failWriteSettings();
                    Log.d(TAG, "write settings: connection state change status: " + status);
                }
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices();
                } else {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failWriteSettings();
                    Log.d(TAG, "write settings: mtu changed status: " + status);
                }
            }

            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    JSONObject jsonSettings = new JSONObject();
                    JSONArray feedings = new JSONArray();
                    try {
                        TimeZone timeZone = Calendar.getInstance().getTimeZone();
                        Date date = Calendar.getInstance(TimeZone.getDefault()).getTime();
                        long milliseconds = date.getTime();
                        milliseconds += timeZone.getOffset(milliseconds);
                        jsonSettings.put("timestamp", milliseconds / 1000L);
                        jsonSettings.put("set_scales_zero", model.setScalesZero);
                        for (int i = 0; i < FeederModel.FEEDING_COUNT; i++) {
                            JSONObject feeding = new JSONObject();
                            feeding.put("mn", 60 * model.feedings[i].hour + model.feedings[i].minute);
                            feeding.put("weight", model.feedings[i].weight);
                            feedings.put(feeding);
                        }
                        jsonSettings.put("feedings", feedings);
                    } catch (JSONException e) {
                        btDriver.stopGattClient();
                        handler.removeCallbacks(runnable);
                        failWriteSettings();
                        Log.d(TAG, "write settings: bad json: " + e.getMessage());
                        return;
                    }
                    BluetoothGattService gattService = gatt.getService(feederServiceUUID);
                    BluetoothGattCharacteristic state = gattService.getCharacteristic(stateGattCharacteristicUUID);
                    state.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    state.setValue(jsonSettings.toString());
                    gatt.writeCharacteristic(state);
                } else {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failWriteSettings();
                    Log.d(TAG, "write settings: services discovered status: " + status);
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                UUID uuid = characteristic.getUuid();
                if (uuid.equals(stateGattCharacteristicUUID)) {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    switch (status) {
                        case BluetoothGatt.GATT_SUCCESS:
                            if (model.setScalesZero) {
                                model.setScalesZero = false;
                                model.weight = 0;
                            }
                            model.viewState = SETTINGS_WRITE_SUCCESS;
                            break;
                        case FEEDER_BUSY:
                            model.viewState = SETTINGS_WRITE_FEEDER_BUSY;
                            break;
                        default:
                            model.viewState = SETTINGS_WRITE_FAIL;
                            Log.d(TAG, "write settings: characteristic write status: " + status);
                            break;
                    }
                    if (fragment != null) {
                        fragment.requireActivity().runOnUiThread(() -> fragment.updateView());
                    }
                }
            }
        });
        return true;
    }

    public boolean readSettings() {
        if (btDriver.isGattClientBusy()) {
            return false;
        }

        Handler handler = new Handler();
        Runnable runnable = () -> {
            if (model.viewState == SETTINGS_READ_PROGRESS) {
                btDriver.stopGattClient();
                failReadSettings();
                Log.d(TAG, "read settings: timeout");
            }
        };
        handler.postDelayed(runnable, ru.vukit.pf.bluetooth.Driver.BLE_OPERATION_DURATION);

        btDriver.startGattClient(model.address, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                    gatt.requestMtu(FEEDER_BLE_MTU_SIZE);
                } else {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failReadSettings();
                    Log.d(TAG, "read settings: connection state change status: " + status);
                }
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices();
                } else {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failReadSettings();
                    Log.d(TAG, "read settings: mtu changed status: " + status);
                }
            }

            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BluetoothGattService gattService = gatt.getService(feederServiceUUID);
                    gatt.readCharacteristic(gattService.getCharacteristic(stateGattCharacteristicUUID));
                } else {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    failReadSettings();
                    Log.d(TAG, "read settings: services discovered status: " + status);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                UUID uuid = characteristic.getUuid();
                if (uuid.equals(stateGattCharacteristicUUID)) {
                    btDriver.stopGattClient();
                    handler.removeCallbacks(runnable);
                    switch (status) {
                        case BluetoothGatt.GATT_SUCCESS:
                            String response = new String(characteristic.getValue());
                            try {
                                JSONObject jsonObj = new JSONObject(response);
                                model.temperature = jsonObj.getDouble("temperature");
                                model.weight = jsonObj.getInt("weight");
                                JSONArray feedings = jsonObj.getJSONArray("feedings");
                                for (int i = 0; i < FeederModel.FEEDING_COUNT; i++) {
                                    JSONObject c = feedings.getJSONObject(i);
                                    int mn = c.getInt("mn");
                                    int minute = mn % 60;
                                    model.feedings[i].hour = (mn - minute) / 60;
                                    model.feedings[i].minute = minute;
                                    model.feedings[i].weight = c.getInt("weight");
                                }
                                model.setScalesZero = false;
                                model.feederFirmware = jsonObj.getString("model").split(",")[1].split(":")[1];
                                model.viewState = SETTINGS_READ_SUCCESS;
                            } catch (final JSONException e) {
                                model.viewState = SETTINGS_READ_FAIL;
                                Log.d(TAG, "read settings: bad json: " + response);
                                return;
                            }
                            break;
                        case FEEDER_BUSY:
                            model.viewState = SETTINGS_READ_FEEDER_BUSY;
                            break;
                        default:
                            model.viewState = SETTINGS_READ_FAIL;
                            Log.d(TAG, "read settings: characteristic read status: " + status);
                            break;
                    }
                    if (fragment != null) {
                        fragment.requireActivity().runOnUiThread(() -> fragment.updateView());
                    }
                }
            }
        });

        return true;
    }

    private void failUpdatingFirmware() {
        model.viewState = UPDATING_FIRMWARE_FAIL;
        if (fragment != null) {
            fragment.requireActivity().runOnUiThread(() -> fragment.updateView());
        }
    }

    private void failFeed() {
        model.viewState = FEED_FAIL;
        if (fragment != null) {
            fragment.requireActivity().runOnUiThread(() -> fragment.updateView());
        }
    }

    private void failWriteSettings() {
        model.viewState = SETTINGS_WRITE_FAIL;
        if (fragment != null) {
            fragment.requireActivity().runOnUiThread(() -> fragment.updateView());
        }
    }

    private void failReadSettings() {
        model.viewState = SETTINGS_READ_FAIL;
        if (fragment != null) {
            fragment.requireActivity().runOnUiThread(() -> fragment.updateView());
        }
    }


}