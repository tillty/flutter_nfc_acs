package com.tillty.flutter_nfc_acs

import android.annotation.SuppressLint
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

internal class DeviceScanner(private val adapter: BluetoothAdapter, override val activity: Activity) :
  BluetoothPermissions(), EventChannel.StreamHandler {
  private var btDevices: HashMap<String, String>? = null
  private var events: EventSink? = null
  private var scanning = false
  private val scanner: BluetoothLeScanner?
    get() = adapter.bluetoothLeScanner

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
    @SuppressLint("MissingPermission")
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

  @SuppressLint("MissingPermission")
  private fun startScan() {
    btDevices = HashMap()
    scanning = true

    Handler(Looper.getMainLooper()).postDelayed({
      if (scanning) {
        stopScan()
      }
    }, SCAN_PERIOD)
    scanner?.startScan(scanCallback)
  }

  @SuppressLint("MissingPermission")
  private fun stopScan() {
    scanner?.stopScan(scanCallback)
    scanning = false
  }

  override fun afterPermissionsGranted() {
    startScan()
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