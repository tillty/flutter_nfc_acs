package com.tillty.flutter_nfc_acs

import android.app.Activity
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ContentValues
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.acs.bluetooth.Acr1255uj1Reader
import com.acs.bluetooth.BluetoothReader
import com.acs.bluetooth.BluetoothReaderGattCallback
import com.acs.bluetooth.BluetoothReaderManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/** FlutterNfcAcsPlugin */
class FlutterNfcAcsPlugin : BluetoothPermissions(), FlutterPlugin, ActivityAware,
    MethodChannel.MethodCallHandler,
    EventChannel.StreamHandler, LifecycleObserver {
    // Flutter channels
    private lateinit var channel: MethodChannel
    private lateinit var devicesChannel: EventChannel

    // These are hooked up on a successful connection to a device.
    private lateinit var deviceBatteryChannel: EventChannel
    private lateinit var deviceStatusChannel: EventChannel
    private lateinit var deviceCardChannel: EventChannel

    // The sink for status events
    private var context: Context? = null
    private var statusEvents: EventChannel.EventSink? = null
    private var activityBinding: ActivityPluginBinding? = null
    private var bluetoothManager: BluetoothManager? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mBluetoothReaderManager: BluetoothReaderManager? = null
    private var mGattCallback: BluetoothReaderGattCallback? = null

    // A DeviceScanner scans for bluetooth devices
    private var deviceScanner: DeviceScanner? = null
    private var batteryStreamHandler: BatteryStreamHandler? = null
    private var cardStreamHandler: CardStreamHandler? = null

    // Connection state
    private var mConnectState: Int = BluetoothReader.STATE_DISCONNECTED

    // Variables for pending permissions
    private var pendingMethodCall: MethodCall? = null
    private var pendingResult: MethodChannel.Result? = null
    private var pendingResultComplete = false

    // The address is kept in memory in case of life cycle events
    private var address: String? = null

    companion object {
        // The method channel's commands
        private const val CONNECT = "CONNECT"
        private const val DISCONNECT = "DISCONNECT"

        // Error codes
        // TODO: Figure out how to transmit errors that are detected in listeners.
        // static final String ERROR_NO_BLUETOOTH_MANAGER = "no_bluetooth_manager";
        // static final String ERROR_GATT_CONNECTION_FAILED = "gatt_connection_failed";
        private const val ERROR_MISSING_ADDRESS = "missing_address"
        private const val ERROR_DEVICE_NOT_FOUND = "device_not_found"
        private const val ERROR_DEVICE_NOT_SUPPORTED = "device_not_supported"
        const val ERROR_NO_PERMISSIONS = "no_permissions"
        private const val CONNECTED = "CONNECTED"
        private const val CONNECTING = "CONNECTING"
        private const val DISCONNECTED = "DISCONNECTED"
        private const val DISCONNECTING = "DISCONNECTING"
        private const val UNKNOWN_CONNECTION_STATE = "UNKNOWN_CONNECTION_STATE"

        // Sleep mode options
        /*private static final byte SLEEP_60_SEC = 0x00;
          private static final byte SLEEP_90_SEC = 0x01;
          private static final byte SLEEP_120_SEC = 0x02;
          private static final byte SLEEP_180_SEC = 0x03;*/
        private const val SLEEP_NEVER: Byte = 0x04

        // "ACR1255U-J1 Auth" in text;
        private val DEFAULT_1255_MASTER_KEY =
            byteArrayOf(65.toByte(), 67, 82, 49, 50, 53, 53, 85, 45, 74, 49, 32, 65, 117, 116, 104)
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter.tillty.com/nfc/acs")
        devicesChannel = EventChannel(flutterPluginBinding.binaryMessenger, "flutter.tillty.com/nfc/acs/devices")
        deviceBatteryChannel = EventChannel(flutterPluginBinding.binaryMessenger, "flutter.tillty.com/nfc/acs/device/battery")
        deviceStatusChannel = EventChannel(flutterPluginBinding.binaryMessenger, "flutter.tillty.com/nfc/acs/device/status")
        deviceCardChannel = EventChannel(flutterPluginBinding.binaryMessenger, "flutter.tillty.com/nfc/acs/device/card")
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = null
        channel.setMethodCallHandler(null)
        devicesChannel.setStreamHandler(null)
        deviceBatteryChannel.setStreamHandler(null)
        deviceStatusChannel.setStreamHandler(null)
        deviceCardChannel.setStreamHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        init()
    }

    override fun onDetachedFromActivityForConfigChanges() {
        dispose()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityBinding = binding
        init()
    }

    override fun onDetachedFromActivity() {
        dispose()
    }

    override val activity: Activity
        get() = activityBinding!!.activity

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        pendingMethodCall = call
        pendingResult = result
        pendingResultComplete = false
        when (call.method) {
            CONNECT -> {
                if (hasPermissions()) {
                    requestPermissions()
                    return
                }
                address = call.argument<String>("address")
                if (address == null) {
                    Handler(Looper.getMainLooper()).post {
                        result.error(
                            ERROR_MISSING_ADDRESS,
                            "The address argument cannot be null",
                            null
                        )
                    }
                    return
                }
                if (connectToReader()) {
                    Handler(Looper.getMainLooper()).post { result.success(null) }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        result.error(
                            ERROR_DEVICE_NOT_FOUND,
                            "The bluetooth device could not be found",
                            null
                        )
                    }
                }
            }
            DISCONNECT -> {
                if (hasPermissions()) {
                    requestPermissions()
                    return
                }
                disconnectFromReader()
                Handler(Looper.getMainLooper()).post { result.success(null) }
            }
            else -> {}
        }
    }

    // Emits status events on listen
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        statusEvents = events
        notifyStatusListeners()
    }

    override fun onCancel(arguments: Any?) {
        statusEvents = null
    }

    override fun afterPermissionsGranted() {
        if (pendingResultComplete) return
        pendingResultComplete = true
        val pmc = pendingMethodCall
        if (pmc != null) {
            when (pmc.method) {
                CONNECT -> {
                    address = pmc.argument<String>("address")
                    if (address == null) {
                        Handler(Looper.getMainLooper()).post {
                            pendingResult?.error(
                                ERROR_MISSING_ADDRESS,
                                "The address argument cannot be null",
                                null
                            )
                        }
                        return
                    }
                    if (connectToReader()) {
                        Handler(Looper.getMainLooper()).post {
                            pendingResult?.success(null)
                        }
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            pendingResult?.error(
                                ERROR_DEVICE_NOT_FOUND,
                                "The bluetooth device could not be found",
                                null
                            )
                        }
                    }
                }
                DISCONNECT -> {
                    disconnectFromReader()
                    Handler(Looper.getMainLooper()).post {
                        pendingResult?.success(null)
                    }
                }
                else -> {}
            }
        }
    }

    override fun afterPermissionsDenied() {
        if (pendingResultComplete) return
        pendingResultComplete = true
        Handler(Looper.getMainLooper()).post {
            pendingResult?.error(ERROR_NO_PERMISSIONS, "Location permissions are required", null)
        }
    }

    private fun init() {
        val lifecycle: Lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(activityBinding!!)
        lifecycle.addObserver(this)
        bluetoothManager = context!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (bluetoothManager == null) return
        setupReaderManager()
        setupGattCallback()
        channel.setMethodCallHandler(this)
        activityBinding!!.addRequestPermissionsResultListener(this)
        deviceScanner = DeviceScanner(bluetoothManager!!.adapter, activityBinding!!.activity)
        devicesChannel.setStreamHandler(deviceScanner)
        activityBinding!!.addRequestPermissionsResultListener(deviceScanner!!)
        deviceStatusChannel.setStreamHandler(this)
        cardStreamHandler = CardStreamHandler()
        deviceCardChannel.setStreamHandler(cardStreamHandler)
        batteryStreamHandler = BatteryStreamHandler()
        deviceBatteryChannel.setStreamHandler(batteryStreamHandler)
    }

    private fun dispose() {
        disconnectFromReader()
        devicesChannel.setStreamHandler(null)
        channel.setMethodCallHandler(null)
        deviceCardChannel.setStreamHandler(null)
        cardStreamHandler!!.dispose()
        deviceBatteryChannel.setStreamHandler(null)
        batteryStreamHandler!!.dispose()
        deviceStatusChannel.setStreamHandler(null)
        activityBinding?.removeRequestPermissionsResultListener(deviceScanner!!)
        activityBinding?.removeRequestPermissionsResultListener(this)
        mGattCallback?.setOnConnectionStateChangeListener(null)
        mGattCallback = null
    }

    /**
     * The reader manager is responsible for setting up all the event streams when a compatible device is detected.
     */
    private fun setupReaderManager() {
        // When a reader is detected.
        mBluetoothReaderManager = BluetoothReaderManager()
        mBluetoothReaderManager?.setOnReaderDetectionListener { reader: BluetoothReader ->
            if (reader !is Acr1255uj1Reader) {
                Handler(Looper.getMainLooper()).post {
                    statusEvents?.error(ERROR_DEVICE_NOT_SUPPORTED, "Device not supported", null)
                }
                Log.w(ContentValues.TAG, "Reader not supported")
                disconnectFromReader()
                return@setOnReaderDetectionListener
            }
            batteryStreamHandler!!.setReader(reader)
            setupAuthenticationListener(reader)
            reader.setOnEnableNotificationCompleteListener { bluetoothReader: BluetoothReader, result: Int ->
                if (result != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(ContentValues.TAG, "Enabling notifications failed")
                } else if (!bluetoothReader.authenticate(DEFAULT_1255_MASTER_KEY)) {
                    Log.w(ContentValues.TAG, "Card reader not ready")
                }
            }

            // Enables the reader's battery level, card status and response notifications.
            if (!reader.enableNotification(true)) {
                Log.w(ContentValues.TAG, "ENABLE NOTIFICATIONS NOT READY!")
            }
        }
    }

    private fun setupAuthenticationListener(reader: BluetoothReader) {
        reader.setOnAuthenticationCompleteListener { r: BluetoothReader, errorCode: Int ->
            if (errorCode == BluetoothReader.ERROR_SUCCESS) {
                Log.i(ContentValues.TAG, "Authentication successful")

                // When a compatible reader is detected, we hook up the event streams.
                cardStreamHandler!!.setReader(r)
                reader.setOnEscapeResponseAvailableListener { re: BluetoothReader, _: ByteArray?, code: Int ->
                    re.setOnEscapeResponseAvailableListener(null)
                    if (code == BluetoothReader.ERROR_SUCCESS) {
                        cardStreamHandler!!.startPolling()
                    } else {
                        Log.w(ContentValues.TAG, "Authentication failed")
                    }
                }
                val sleepModeFormat = byteArrayOf(0xE0.toByte(), 0x00, 0x00, 0x48, SLEEP_NEVER)
                reader.transmitEscapeCommand(sleepModeFormat)
            } else {
                Log.w(ContentValues.TAG, "Authentication failed")
            }
        }
    }

    /**
     * Monitors the connection, and if one is established, detects the reader type in the other end.
     */
    private fun setupGattCallback() {
        // When a connection to GATT is established.
        mGattCallback = BluetoothReaderGattCallback()
        mGattCallback?.setOnConnectionStateChangeListener { gatt: BluetoothGatt?, state: Int, newState: Int ->
            if (state != BluetoothGatt.GATT_SUCCESS) {
                setConnectionState(BluetoothReader.STATE_DISCONNECTED)
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w(ContentValues.TAG, "Could not connect to GATT")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w(ContentValues.TAG, "Could not disconnect from GATT")
                }
                return@setOnConnectionStateChangeListener
            }
            setConnectionState(newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mBluetoothReaderManager?.detectReader(gatt, mGattCallback)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mBluetoothGatt!!.disconnect()
                mBluetoothGatt!!.close()
                mBluetoothGatt = null
                setConnectionState(BluetoothReader.STATE_DISCONNECTED)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun connectIfDisconnected() {
        if (address != null && mConnectState == BluetoothReader.STATE_DISCONNECTED) {
            connectToReader()
        }
    }

    private fun connectToReader(): Boolean {
        if (address == null) {
            return false
        }
        if (bluetoothManager == null) {
            setConnectionState(BluetoothReader.STATE_DISCONNECTED)
            Log.e(
                ContentValues.TAG,
                "BluetoothManager was null - cannot connect. The device might not have a bluetooth adapter."
            )
            return false
        }
        val bluetoothAdapter = bluetoothManager!!.adapter
        if (!bluetoothAdapter.isEnabled) {
            Log.w(ContentValues.TAG, "Bluetooth was not enabled!")
            return false
        }
        val device = bluetoothAdapter.getRemoteDevice(address)
        if (device == null) {
            Log.w(ContentValues.TAG, "Device not found. Unable to connect.")
            return false
        }
        if (mBluetoothGatt != null) {
            mBluetoothGatt!!.disconnect()
            mBluetoothGatt!!.close()
        }

        // Connect to the GATT server.
        setConnectionState(BluetoothReader.STATE_CONNECTING)
        mBluetoothGatt = device.connectGatt(context, false, mGattCallback)
        return true
    }

    /**
     * Disconnects the reader and releases resources that are dependant on being connected, which are irrelevant when disconnected.
     */
    private fun disconnectFromReader() {
        // Close existing GATT connection
        if (mBluetoothGatt != null) {
            mBluetoothGatt!!.disconnect()
        }
        setConnectionState(BluetoothReader.STATE_DISCONNECTED)
    }

    private fun setConnectionState(connectionState: Int) {
        mConnectState = connectionState
        notifyStatusListeners()
    }

    private fun notifyStatusListeners() {
        // We can't send a status back if no one is listening for it.
        when (mConnectState) {
            BluetoothReader.STATE_CONNECTED -> Handler(Looper.getMainLooper()).post {
                statusEvents?.success(CONNECTED)
            }
            BluetoothReader.STATE_CONNECTING -> Handler(Looper.getMainLooper()).post {
                statusEvents?.success(CONNECTING)
            }
            BluetoothReader.STATE_DISCONNECTED -> Handler(Looper.getMainLooper()).post {
                statusEvents?.success(DISCONNECTED)
            }
            BluetoothReader.STATE_DISCONNECTING -> Handler(Looper.getMainLooper()).post {
                statusEvents?.success(DISCONNECTING)
            }
            else -> Handler(Looper.getMainLooper()).post {
                statusEvents?.success(UNKNOWN_CONNECTION_STATE)
            }
        }
    }
}