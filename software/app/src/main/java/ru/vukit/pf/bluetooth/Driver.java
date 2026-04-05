package ru.vukit.pf.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

@SuppressLint("MissingPermission")
public class Driver {
    private static Driver INSTANCE = null;
    public final static Long BLE_OPERATION_DURATION = 10000L; // milliseconds
    private final BluetoothAdapter btAdapter;
    private BluetoothGatt connectGatt;
    private final Handler handlerLeScanner = new Handler();
    private Runnable runnableLeScanner;
    private boolean isScanning;

    private Driver(Context ctx) {
        BluetoothManager btManager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
    }

    public static synchronized Driver getInstance(@Nullable Context ctx) {
        if (ctx == null) {
            return INSTANCE;
        }
        if (INSTANCE == null) {
            INSTANCE = new Driver(ctx);
        }
        return INSTANCE;
    }

    public void Enable() {
        btAdapter.enable();
    }

    public void Disable() {
        btAdapter.disable();
    }

    public boolean isEnabled() {
        try {
            return btAdapter.isEnabled();
        } catch (NullPointerException ex) {
            return false;
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    public void startScanLeDevice(ScanCallback mLeScanCallback) {
        BluetoothLeScanner btLeScanner = btAdapter.getBluetoothLeScanner();
        runnableLeScanner = () -> {
            btLeScanner.stopScan(mLeScanCallback);
            isScanning = false;
        };
        handlerLeScanner.postDelayed(runnableLeScanner, BLE_OPERATION_DURATION);
        btLeScanner.startScan(mLeScanCallback);
        isScanning = true;
    }

    public void stopScanLeDevice(ScanCallback mLeScanCallback) {
        if (runnableLeScanner != null) {
            handlerLeScanner.removeCallbacks(runnableLeScanner);
            runnableLeScanner = null;
        }
        if (isScanning) {
            BluetoothLeScanner btLeScanner = btAdapter.getBluetoothLeScanner();
            btLeScanner.stopScan(mLeScanCallback);
            isScanning = false;
        }
    }

    public void startGattClient(String mac_address, BluetoothGattCallback bluetoothCattCallback) {
        BluetoothDevice device = btAdapter.getRemoteDevice(mac_address);
        connectGatt = device.connectGatt(null, true, bluetoothCattCallback);
    }

    public void stopGattClient() {
        if (connectGatt != null) {
            connectGatt.disconnect();
            connectGatt.close();
            connectGatt = null;
        }
    }

    public boolean isGattClientBusy() {
        return connectGatt != null;
    }
}
