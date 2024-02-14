package com.tillty.flutter_nfc_acs

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ContentValues
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import java.util.*

internal class DeviceScanner(adapter: BluetoothAdapter, activity: Activity) :
  BluetoothPermissions(), EventChannel.StreamHandler {
  private var btDevices: HashMap<String, String>? = null
  private val bluetoothAdapter: BluetoothAdapter?
  private var events: EventSink? = null
  private var scanning = false
  private val scanner: BluetoothLeScanner
  override val activity: Activity

  init {
    bluetoothAdapter = adapter
    scanner = adapter.bluetoothLeScanner
    this.activity = activity
  }

  companion object {
    private const val SCAN_PERIOD: Long = 10000
  }

  override fun onListen(arguments: Any?, events: EventSink?) {
    this.events = events
    // TODO: Ask for permissions to turn on bluetooth if disabled.
    if (hasPermissions()) {
      requestPermissions()
      return
    }
    startScan()
  }

  override fun onCancel(arguments: Any?) {
    stopScan()
    events = null
  }

  private val scanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
      val e = events
      if (e != null) {
        Handler(Looper.getMainLooper()).post {
          val device = result.device
          if (!btDevices!!.containsKey(device.address)) {
            btDevices!![device.address] = device.name ?: "N/A"
            e.success(btDevices)
          }
        }
      } else {
        Log.w(
          ContentValues.TAG,
          "Could not output devices, because the event sink was null"
        )
      }
    }
  }

  private fun startScan() {
    btDevices = HashMap()
    scanning = true

    Handler(Looper.getMainLooper()).postDelayed({
      if (scanning) {
        stopScan()
      }
    }, SCAN_PERIOD)
    scanner.startScan(scanCallback)
  }

  private fun stopScan() {
    scanner.stopScan(scanCallback)
    scanning = false
  }

  override fun afterPermissionsGranted() {
    if (bluetoothAdapter != null) {
      startScan()
    } else {
      Log.e(ContentValues.TAG, "Bluetooth adapter was null, in the permission callback")
    }
  }

  override fun afterPermissionsDenied() {
    if (events != null) {
      events!!.error(
        FlutterNfcAcsPlugin.ERROR_NO_PERMISSIONS,
        "Location permissions are required",
        null
      )
      events = null
    }
  }
}