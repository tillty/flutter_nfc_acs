package com.tillty.flutter_nfc_acs

import android.content.ContentValues
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.acs.bluetooth.Acr1255uj1Reader
import com.acs.bluetooth.BluetoothReader
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink

/**
 * A StreamHandler that emits the IDs of scanned cards.
 */
internal class CardStreamHandler : EventChannel.StreamHandler {
  private var reader: BluetoothReader? = null
  private var events: EventSink? = null

  companion object {
    private val AUTO_POLLING_START = byteArrayOf(0xE0.toByte(), 0x00, 0x00, 0x40, 0x01)
    private val AUTO_POLLING_STOP = byteArrayOf(0xE0.toByte(), 0x00, 0x00, 0x40, 0x00)
    private const val requestCardId = "FFCA000000"
  }

  fun setReader(reader: BluetoothReader) {
    if (reader is Acr1255uj1Reader) {
      this.reader = reader
      reader.setOnResponseApduAvailableListener { _: BluetoothReader?, response: ByteArray, errorCode: Int ->
        if (errorCode == BluetoothReader.ERROR_SUCCESS) {
          Handler(Looper.getMainLooper()).post {
            if (events != null) {
              events!!.success(
                Utils.toHexString(
                  response.copyOf(response.size - 2)
                ).trim { it <= ' ' })
            }
          }
        } else {
          Handler(Looper.getMainLooper()).post {
            if (events != null) {
              events!!.error("unknown_reader_error", errorCode.toString(), null)
            }
          }
        }
      }
      reader.setOnCardStatusChangeListener { bluetoothReader: BluetoothReader, cardStatusCode: Int ->
        Log.i(ContentValues.TAG, "Card status: " + getCardStatusString(cardStatusCode))
        if (cardStatusCode == BluetoothReader.CARD_STATUS_PRESENT) {
          bluetoothReader.transmitApdu(Utils.hexStringToByteArray(requestCardId))
        }
      }
    } else {
      Log.i(ContentValues.TAG, "Card stream not supported for this device")
    }
  }

  private fun getCardStatusString(cardStatus: Int): String {
    return when (cardStatus) {
      BluetoothReader.CARD_STATUS_ABSENT -> {
        "Absent"
      }

      BluetoothReader.CARD_STATUS_PRESENT -> {
        "Present"
      }

      BluetoothReader.CARD_STATUS_POWERED -> {
        "Powered"
      }

      BluetoothReader.CARD_STATUS_POWER_SAVING_MODE -> {
        "Power saving mode"
      }

      else -> "Unknown"
    }
  }

  fun startPolling() {
    if (reader != null) {
      reader!!.transmitEscapeCommand(AUTO_POLLING_START)
    }
  }

  override fun onListen(arguments: Any?, events: EventSink?) {
    this.events = events
    startPolling()
  }

  override fun onCancel(arguments: Any?) {
    dispose()
  }

  fun dispose() {
    reader?.transmitEscapeCommand(AUTO_POLLING_STOP)
    reader?.setOnResponseApduAvailableListener(null)
    reader?.setOnCardStatusChangeListener(null)
    reader = null
    events = null
  }
}