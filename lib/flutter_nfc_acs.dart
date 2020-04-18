import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_nfc_acs/models.dart';

class FlutterNfcAcs {
  static const MethodChannel _channel = const MethodChannel('flutter.nuvopoint.com/nfc/acs');
  static const EventChannel _devicesChannel = const EventChannel('flutter.nuvopoint.com/nfc/acs/devices');
  static const EventChannel _deviceBatteryChannel = const EventChannel('flutter.nuvopoint.com/nfc/acs/device/battery');
  static const EventChannel _deviceStatusChannel = const EventChannel("flutter.nuvopoint.com/nfc/acs/device/status");
  static const EventChannel _deviceCardChannel = const EventChannel("flutter.nuvopoint.com/nfc/acs/device/card");

  // _channel's commands
  static const String CONNECT = 'CONNECT';
  static const String DISCONNECT = 'DISCONNECT';

  // The _deviceStatusChannel's outputs
  static const String CONNECTED = "CONNECTED";
  static const String CONNECTING = "CONNECTING";
  static const String DISCONNECTED = "DISCONNECTED";
  static const String DISCONNECTING = "DISCONNECTING";
  static const String UNKNOWN_CONNECTION_STATE = "UNKNOWN_CONNECTION_STATE";

  static Stream<List<AcsDevice>> _devices;
  static Stream<String> _connectionStatus;
  static Stream<String> _cards;
  static Stream<int> _batteryStatus;

  static Stream<List<AcsDevice>> get devices {
    _devices ??= _devicesChannel.receiveBroadcastStream().map<List<AcsDevice>>((data) {
      return (data as Map<dynamic, dynamic>).entries.map<AcsDevice>((m) => AcsDevice(m.key, m.value)).toList();
    });

    return _devices;
  }

  static Stream<String> get connectionStatus {
    _connectionStatus ??= _deviceStatusChannel.receiveBroadcastStream().map<String>((data) {
      return data as String;
    });

    return _connectionStatus;
  }

  static Stream<String> get cards {
    _cards ??= _deviceCardChannel.receiveBroadcastStream().map<String>((data) {
      return data as String;
    });

    return _cards;
  }

  static Stream<int> get batteryStatus {
    _batteryStatus ??= _deviceBatteryChannel.receiveBroadcastStream().map<int>((data) {
      return data as int;
    });

    return _batteryStatus;
  }

  static Future<void> connect(AcsDevice device) {
    return _channel.invokeMethod(CONNECT, {'address': device.address});
  }

  static Future<void> disconnect() {
    return _channel.invokeMethod(DISCONNECT);
  }
}
