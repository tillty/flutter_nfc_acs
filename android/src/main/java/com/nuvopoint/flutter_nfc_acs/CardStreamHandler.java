package com.nuvopoint.flutter_nfc_acs;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.acs.bluetooth.Acr1255uj1Reader;
import com.acs.bluetooth.BluetoothReader;

import java.util.Arrays;

import io.flutter.plugin.common.EventChannel;

import static android.content.ContentValues.TAG;

/**
 * A StreamHandler that emits the IDs of scanned cards.
 */
class CardStreamHandler implements EventChannel.StreamHandler {
  private static final byte[] AUTO_POLLING_START = {(byte) 0xE0, 0x00, 0x00, 0x40, 0x01};
  private static final byte[] AUTO_POLLING_STOP = {(byte) 0xE0, 0x00, 0x00, 0x40, 0x00};
  private static final String requestCardId = "FFCA000000";
  private BluetoothReader reader;
  private EventChannel.EventSink events;

  void setReader(final BluetoothReader reader) {
    if (reader instanceof Acr1255uj1Reader) {
      this.reader = reader;

      reader.setOnResponseApduAvailableListener((_r, response, errorCode) -> {
        if (events != null) {
          if (errorCode == BluetoothReader.ERROR_SUCCESS) {
            new Handler(Looper.getMainLooper()).post(() -> events.success(Utils.toHexString(Arrays.copyOf(response, response.length - 2))));
          } else {
            new Handler(Looper.getMainLooper()).post(() -> events.error("unknown_reader_error", String.valueOf(errorCode), null));
          }
        }
      });

      reader.setOnCardStatusChangeListener((bluetoothReader, cardStatusCode) -> {
        Log.i(TAG, "Card status: " + getCardStatusString(cardStatusCode));
        if (cardStatusCode == BluetoothReader.CARD_STATUS_PRESENT) {
          bluetoothReader.transmitApdu(Utils.hexString2Bytes(requestCardId));
        }
      });
    } else {
      Log.i(TAG, "Card stream not supported for this device");
    }
  }

  private String getCardStatusString(int cardStatus) {
    if (cardStatus == BluetoothReader.CARD_STATUS_ABSENT) {
      return "Absent";
    } else if (cardStatus == BluetoothReader.CARD_STATUS_PRESENT) {
      return "Present";
    } else if (cardStatus == BluetoothReader.CARD_STATUS_POWERED) {
      return "Powered";
    } else if (cardStatus == BluetoothReader.CARD_STATUS_POWER_SAVING_MODE) {
      return "Power saving mode";
    }

    return "Unknown";
  }

  void startPolling() {
    if (reader != null) {
      reader.transmitEscapeCommand(AUTO_POLLING_START);
    }
  }

  @Override
  public void onListen(Object arguments, EventChannel.EventSink events) {
    this.events = events;
    startPolling();
  }

  @Override
  public void onCancel(Object arguments) {
    dispose();
  }

  void dispose() {
    if (reader != null) {
      reader.transmitEscapeCommand(AUTO_POLLING_STOP);
      reader.setOnResponseApduAvailableListener(null);
      reader.setOnCardStatusChangeListener(null);
    }
    events = null;
  }
}
