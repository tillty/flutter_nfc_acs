package com.nuvopoint.flutter_nfc_acs;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.acs.bluetooth.Acr1255uj1Reader;
import com.acs.bluetooth.BluetoothReader;
import com.acs.bluetooth.BluetoothReaderGattCallback;
import com.acs.bluetooth.BluetoothReaderManager;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;

import static android.content.ContentValues.TAG;

/**
 * FlutterNfcAcsPlugin
 */
public class FlutterNfcAcsPlugin extends BluetoothPermissions implements FlutterPlugin, ActivityAware, MethodCallHandler, StreamHandler {
  // The method channel's commands
  private static final String CONNECT = "CONNECT";
  private static final String DISCONNECT = "DISCONNECT";

  // Error codes
  // TODO: Figure out how to transmit errors that are detected in listeners.
  // static final String ERROR_NO_BLUETOOTH_MANAGER = "no_bluetooth_manager";
  // static final String ERROR_GATT_CONNECTION_FAILED = "gatt_connection_failed";
  private static final String ERROR_MISSING_ADDRESS = "missing_address";
  private static final String ERROR_DEVICE_NOT_FOUND = "device_not_found";
  private static final String ERROR_DEVICE_NOT_SUPPORTED = "device_not_supported";
  static final String ERROR_NO_PERMISSIONS = "no_permissions";

  // Flutter channels
  private MethodChannel channel;
  private EventChannel devicesChannel;
  // These are hooked up on a successful connection to a device.
  private EventChannel deviceBatteryChannel;
  private EventChannel deviceStatusChannel;
  private EventChannel deviceCardChannel;

  // The sink for status events
  private EventChannel.EventSink statusEvents;
  private static final String CONNECTED = "CONNECTED";
  private static final String CONNECTING = "CONNECTING";
  private static final String DISCONNECTED = "DISCONNECTED";
  private static final String DISCONNECTING = "DISCONNECTING";
  private static final String UNKNOWN_CONNECTION_STATE = "UNKNOWN_CONNECTION_STATE";

  // "ACR1255U-J1 Auth" in text;
  private static final byte[] DEFAULT_1255_MASTER_KEY = {(byte) 65, 67, 82, 49, 50, 53, 53, 85, 45, 74, 49, 32, 65, 117, 116, 104};
  private static final String requestTurnOffSleepMode = "E000004805";

  private ActivityPluginBinding activityBinding;
  private BluetoothManager bluetoothManager;
  private BluetoothGatt mBluetoothGatt;
  private BluetoothReaderManager mBluetoothReaderManager;
  private BluetoothReaderGattCallback mGattCallback;
  private BluetoothReader reader;
  private Context context;

  // A DeviceScanner scans for bluetooth devices
  private DeviceScanner deviceScanner;
  private BatteryStreamHandler batteryStreamHandler;
  private CardStreamHandler cardStreamHandler;

  // Connection state
  private int mConnectState = BluetoothReader.STATE_DISCONNECTED;

  // Variables for pending permissions
  private MethodCall pendingMethodCall;
  private MethodChannel.Result pendingResult;

  // TEMP
  //private BluetoothDevice device;
  //private BluetoothGatt gatt;

  @Override
  public void onAttachedToEngine(final @NonNull FlutterPluginBinding flutterPluginBinding) {
    context = flutterPluginBinding.getApplicationContext();
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "flutter.nuvopoint.com/nfc/acs");
    devicesChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "flutter.nuvopoint.com/nfc/acs/devices");
    deviceBatteryChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "flutter.nuvopoint.com/nfc/acs/device/battery");
    deviceStatusChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "flutter.nuvopoint.com/nfc/acs/device/status");
    deviceCardChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "flutter.nuvopoint.com/nfc/acs/device/card");
  }

  @Override
  public void onDetachedFromEngine(final @NonNull FlutterPluginBinding binding) {
    context = null;
  }

  @Override
  public void onAttachedToActivity(final @NonNull ActivityPluginBinding binding) {
    activityBinding = binding;
    init();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    dispose();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(final @NonNull ActivityPluginBinding binding) {
    activityBinding = binding;
    init();
  }

  @Override
  public void onDetachedFromActivity() {
    dispose();
  }

  @Override
  protected Activity getActivity() {
    return activityBinding.getActivity();
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
    switch (call.method) {
      case CONNECT:
        if (!hasPermissions()) {
          pendingMethodCall = call;
          pendingResult = result;
          requestPermissions();
          return;
        }

        final String address = call.argument("address");
        if (address == null) {
          new Handler(Looper.getMainLooper()).post(() -> result.error(ERROR_MISSING_ADDRESS, "The address argument cannot be null", null));
          return;
        }

        if (connectToReader(address)) {
          new Handler(Looper.getMainLooper()).post(() -> result.success(null));
        } else {
          new Handler(Looper.getMainLooper()).post(() -> result.error(ERROR_DEVICE_NOT_FOUND, "The bluetooth device could not be found", null));
        }

        break;
      case DISCONNECT:
        if (!hasPermissions()) {
          pendingMethodCall = call;
          pendingResult = result;
          requestPermissions();
          return;
        }

        disconnectFromReader();
        new Handler(Looper.getMainLooper()).post(() -> result.success(null));
        break;
      default:
    }
  }

  // Emits status events on listen
  @Override
  public void onListen(Object arguments, EventChannel.EventSink events) {
    statusEvents = events;
    notifyStatusListeners();
  }

  @Override
  public void onCancel(Object arguments) {
    statusEvents.endOfStream();
    statusEvents = null;
  }

  @Override
  protected void afterPermissionsGranted() {
    switch (pendingMethodCall.method) {
      case CONNECT:
        final String address = pendingMethodCall.argument("address");
        if (address == null) {
          new Handler(Looper.getMainLooper()).post(() -> pendingResult.error(ERROR_MISSING_ADDRESS, "The address argument cannot be null", null));
          return;
        }

        if (connectToReader(address)) {
          new Handler(Looper.getMainLooper()).post(() -> pendingResult.success(null));
        } else {
          new Handler(Looper.getMainLooper()).post(() -> pendingResult.error(ERROR_DEVICE_NOT_FOUND, "The bluetooth device could not be found", null));
        }
        break;
      case DISCONNECT:
        disconnectFromReader();
        new Handler(Looper.getMainLooper()).post(() -> pendingResult.success(null));
        break;
      default:
    }
  }

  @Override
  protected void afterPermissionsDenied() {
    new Handler(Looper.getMainLooper()).post(() -> pendingResult.error(ERROR_NO_PERMISSIONS, "Location permissions are required", null));
  }

  private void init() {
    // TODO: Lifecycle support
    // Lifecycle lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(activityBinding);
    bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    if (bluetoothManager == null) return;

    setupReaderManager();
    setupGattCallback();

    channel.setMethodCallHandler(this);
    activityBinding.addRequestPermissionsResultListener(this);

    deviceScanner = new DeviceScanner(bluetoothManager.getAdapter(), activityBinding.getActivity());
    devicesChannel.setStreamHandler(deviceScanner);
    activityBinding.addRequestPermissionsResultListener(deviceScanner);

    deviceStatusChannel.setStreamHandler(this);

    cardStreamHandler = new CardStreamHandler();
    deviceCardChannel.setStreamHandler(cardStreamHandler);

    batteryStreamHandler = new BatteryStreamHandler();
    deviceBatteryChannel.setStreamHandler(batteryStreamHandler);
  }

  private void dispose() {
    disconnectFromReader();

    devicesChannel.setStreamHandler(null);
    channel.setMethodCallHandler(null);

    deviceCardChannel.setStreamHandler(null);
    cardStreamHandler.dispose();

    deviceBatteryChannel.setStreamHandler(null);
    batteryStreamHandler.dispose();

    if (statusEvents != null) {
      statusEvents.endOfStream();
      statusEvents = null;
    }

    deviceStatusChannel.setStreamHandler(null);

    activityBinding.removeRequestPermissionsResultListener(deviceScanner);
    activityBinding.removeRequestPermissionsResultListener(this);

    mGattCallback.setOnConnectionStateChangeListener(null);
    mGattCallback = null;
  }

  /**
   * The reader manager is responsible for setting up all the event streams when a compatible device is detected.
   */
  private void setupReaderManager() {
    // When a reader is detected.
    mBluetoothReaderManager = new BluetoothReaderManager();
    mBluetoothReaderManager.setOnReaderDetectionListener(reader -> {
      if (!(reader instanceof Acr1255uj1Reader)) {
        if (statusEvents != null)
          new Handler(Looper.getMainLooper()).post(() -> statusEvents.error(ERROR_DEVICE_NOT_SUPPORTED, "Device not supported", null));
        Log.w(TAG, "Reader not supported");
        disconnectFromReader();
        return;
      }

      this.reader = reader;
      reader.transmitEscapeCommand(Utils.hexString2Bytes(requestTurnOffSleepMode));
      batteryStreamHandler.setReader(reader);
      setupAuthenticationListener(reader);

      reader.setOnEnableNotificationCompleteListener((bluetoothReader, result) -> {
        if (result != BluetoothGatt.GATT_SUCCESS) {
          Log.w(TAG, "ENABLE DID NOT GET SET!");
        } else {
          Log.w(TAG, "ENABLE NOTIFICATIONS READY!");

          if (!bluetoothReader.authenticate(DEFAULT_1255_MASTER_KEY)) {
            Log.w(TAG, "CARD READER NOT READY!");
          }
        }
      });

      // Enables the reader's battery level, card status and response notifications.
      if (!reader.enableNotification(true)) {
        Log.w(TAG, "ENABLE NOTIFICATIONS NOT READY!");
      }
    });
  }

  private void setupAuthenticationListener(BluetoothReader reader) {
    reader.setOnAuthenticationCompleteListener((reader2, errorCode) -> {
      if (errorCode == BluetoothReader.ERROR_SUCCESS) {
        Log.i(TAG, "Authentication successful");
        // When a compatible reader is detected, we hook up the event streams.
        cardStreamHandler.setReader(reader2);
        cardStreamHandler.startPolling();
      } else {
        Log.w(TAG, "Authentication failed");
      }
    });
  }

  /**
   * Monitors the connection, and if one is established, detects the reader type in the other end.
   */
  private void setupGattCallback() {
    // When a connection to GATT is established.
    mGattCallback = new BluetoothReaderGattCallback();
    mGattCallback.setOnConnectionStateChangeListener((gatt, state, newState) -> {
      if (state != BluetoothGatt.GATT_SUCCESS) {
        setConnectionState(BluetoothReader.STATE_DISCONNECTED);

        if (newState == BluetoothProfile.STATE_CONNECTED) {
          Log.w(TAG, "Could not connect to GATT");
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
          Log.w(TAG, "Could not disconnect from GATT");
        }
        return;
      }

      setConnectionState(newState);

      if (newState == BluetoothProfile.STATE_CONNECTED) {
        mBluetoothReaderManager.detectReader(gatt, mGattCallback);
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        disconnectFromReader();
      }
    });
  }

  private boolean connectToReader(@NonNull final String address) {
    if (bluetoothManager == null) {
      setConnectionState(BluetoothReader.STATE_DISCONNECTED);
      Log.e(TAG, "BluetoothManager was null - cannot connect. The device might not have a bluetooth adapter.");
      return false;
    }

    BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
    if (!bluetoothAdapter.isEnabled()) {
      Log.w(TAG, "Bluetooth was not enabled!");
    }

    final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

    if (device == null) {
      Log.w(TAG, "Device not found. Unable to connect.");
      return false;
    }

    if (mBluetoothGatt != null) {
      mBluetoothGatt.disconnect();
      mBluetoothGatt.close();
    }

    // Connect to the GATT server.
    setConnectionState(BluetoothReader.STATE_CONNECTING);
    mBluetoothGatt = device.connectGatt(context, false, mGattCallback);

    return true;
  }

  /**
   * Disconnects the reader and releases resources that are dependant on being connected, which are irrelevant when disconnected.
   */
  private void disconnectFromReader() {
    // Close existing GATT connection
    if (mBluetoothGatt != null) {
      mBluetoothGatt.disconnect();
      mBluetoothGatt.close();
      mBluetoothGatt = null;
    }

    setConnectionState(BluetoothReader.STATE_DISCONNECTED);
  }

  private void setConnectionState(int connectionState) {
    mConnectState = connectionState;
    notifyStatusListeners();
  }

  private void notifyStatusListeners() {
    // We can't send a status back if no one is listening for it.
    if (statusEvents != null) {
      switch (mConnectState) {
        case BluetoothReader.STATE_CONNECTED:
          new Handler(Looper.getMainLooper()).post(() -> statusEvents.success(CONNECTED));
          break;
        case BluetoothReader.STATE_CONNECTING:
          new Handler(Looper.getMainLooper()).post(() -> statusEvents.success(CONNECTING));
          break;
        case BluetoothReader.STATE_DISCONNECTED:
          new Handler(Looper.getMainLooper()).post(() -> statusEvents.success(DISCONNECTED));
          break;
        case BluetoothReader.STATE_DISCONNECTING:
          new Handler(Looper.getMainLooper()).post(() -> statusEvents.success(DISCONNECTING));
          break;
        default:
          new Handler(Looper.getMainLooper()).post(() -> statusEvents.success(UNKNOWN_CONNECTION_STATE));
      }
    }
  }
}
