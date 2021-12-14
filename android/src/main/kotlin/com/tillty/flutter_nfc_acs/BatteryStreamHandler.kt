package com.tillty.flutter_nfc_acs

import android.content.ContentValues
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.acs.bluetooth.Acr1255uj1Reader
import com.acs.bluetooth.BluetoothReader
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink

internal class BatteryStreamHandler : EventChannel.StreamHandler {
    private var reader: Acr1255uj1Reader? = null
    private var events: EventSink? = null
    private var batteryLevel = -1

    fun setReader(reader: BluetoothReader?) {
        if (reader is Acr1255uj1Reader) {
            this.reader = reader
            this.reader!!.setOnBatteryLevelChangeListener { _: BluetoothReader?, batteryLevel: Int ->
                this.batteryLevel = batteryLevel
                Handler(Looper.getMainLooper()).post {
                    if (events != null) {
                        events!!.success(batteryLevel)
                    }
                }
            }
            this.reader!!.setOnBatteryLevelAvailableListener { _: BluetoothReader?, batteryLevel: Int, _: Int ->
                this.batteryLevel = batteryLevel
                Handler(Looper.getMainLooper()).post {
                    if (events != null) {
                        events!!.success(batteryLevel)
                    }
                }
            }
            Handler(Looper.getMainLooper()).post {
                if (events != null && batteryLevel != -1) {
                    events!!.success(batteryLevel)
                }
            }
        } else {
            Log.i(ContentValues.TAG, "Battery stream not supported for this device")
        }
    }

    override fun onListen(arguments: Any?, events: EventSink?) {
        this.events = events
        Handler(Looper.getMainLooper()).post {
            if (events != null && batteryLevel != -1) {
                events.success(batteryLevel)
            }
        }
    }

    override fun onCancel(arguments: Any?) {
        dispose()
    }

    fun dispose() {
        if (reader != null) reader!!.setOnBatteryLevelChangeListener(null)
        events = null
    }
}