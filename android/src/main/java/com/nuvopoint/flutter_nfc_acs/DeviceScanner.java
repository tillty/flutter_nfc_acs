package com.nuvopoint.flutter_nfc_acs;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;

import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;

import static android.content.ContentValues.TAG;
import static com.nuvopoint.flutter_nfc_acs.FlutterNfcAcsPlugin.ERROR_NO_PERMISSIONS;

class DeviceScanner extends BluetoothPermissions implements StreamHandler {
  private final HashMap<String, String> btDevices = new HashMap<>();
  private final BluetoothAdapter bluetoothAdapter;
  private EventSink events;
  private final Handler handler;
  private boolean scanning = false;
  private final Activity activity;

  private static final long SCAN_PERIOD = 10000;

  DeviceScanner(@NonNull BluetoothAdapter adapter, @NonNull Activity activity) {
    bluetoothAdapter = adapter;
    this.activity = activity;
    handler = new Handler();
  }

  @Override
  public void onListen(Object arguments, EventSink events) {
    this.events = events;
    // TODO: Ask for permissions to turn on bluetooth if disabled.

    if (!hasPermissions()) {
      requestPermissions();
      return;
    }

    if (!btDevices.isEmpty()){
      events.success(btDevices);
    }

    startScan();
  }

  @Override
  public void onCancel(Object arguments) {
    stopScan();
    events = null;
  }

  /* Device scan callback. */
  private BluetoothAdapter.LeScanCallback mLeScanCallback = (device, rssi, scanRecord) -> {
    if (events != null) {
      new Handler(Looper.getMainLooper()).post(() -> {
        if (!btDevices.containsKey(device.getAddress())) {
          btDevices.put(device.getAddress(), device.getName());
          events.success(btDevices);
        }
      });
    } else {
      Log.w(TAG, "Could not output devices, because the event sink was null");
    }
  };

  private void startScan() {
    handler.postDelayed(() -> {
      if (scanning) {
        scanning = false;
        bluetoothAdapter.stopLeScan(mLeScanCallback);
      }
    }, SCAN_PERIOD);

    scanning = true;
    bluetoothAdapter.startLeScan(mLeScanCallback);
  }

  private void stopScan() {
    bluetoothAdapter.stopLeScan(mLeScanCallback);
    scanning = false;
  }

  @Override
  protected Activity getActivity() {
    return activity;
  }

  @Override
  protected void afterPermissionsGranted() {
    if (bluetoothAdapter != null) {
      startScan();
    } else {
      Log.e(TAG, "Bluetooth adapter was null, in the permission callback");
    }
  }

  @Override
  protected void afterPermissionsDenied() {
    if (events != null) {
      events.error(ERROR_NO_PERMISSIONS, "Location permissions are required", null);
      events = null;
    }
  }
}
