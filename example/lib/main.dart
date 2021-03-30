import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter_nfc_acs/flutter_nfc_acs.dart';
import 'package:flutter_nfc_acs/models.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  late final Stream<List<AcsDevice>> _stream;

  @override
  void initState() {
    super.initState();

    _stream = FlutterNfcAcs.devices.map((d) => (d.where((asc) => (asc.name?.indexOf('ACR') ?? -1) != -1)).toList());
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Flutter NFC ACS'),
        ),
        body: StreamBuilder<List<AcsDevice>>(
          stream: _stream,
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              print(snapshot.error);
              return Text('Error');
            } else if (snapshot.hasData) {
              return ListView.builder(
                itemCount: snapshot.data!.length,
                itemBuilder: (context, i) {
                  final item = snapshot.data![i];
                  return ElevatedButton(
                    key: ValueKey(item.address),
                    child: Text((item.name ?? 'No name') + ' -- ' + item.address),
                    onPressed: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => DeviceRoute(device: item),
                      ),
                    ),
                  );
                },
              );
            } else {
              return Text('No data yet');
            }
          },
        ),
      ),
    );
  }

  @override
  void dispose() {
    super.dispose();
  }
}

class DeviceRoute extends StatefulWidget {
  const DeviceRoute({Key? key, required this.device}) : super(key: key);

  final AcsDevice device;

  @override
  _DeviceRouteState createState() => _DeviceRouteState();
}

class _DeviceRouteState extends State<DeviceRoute> {
  String connection = FlutterNfcAcs.DISCONNECTED;
  String? error;
  StreamSubscription? _sub;

  @override
  void initState() {
    super.initState();

    FlutterNfcAcs.connect(widget.device.address).catchError((err) => setState(() => error = err));

    _sub = FlutterNfcAcs.connectionStatus.listen((status) {
      setState(() {
        connection = status;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            ElevatedButton(
              child: Text(connection == FlutterNfcAcs.DISCONNECTED ? 'Connect' : 'Disconnect'),
              onPressed: () => connection == FlutterNfcAcs.DISCONNECTED
                  ? FlutterNfcAcs.connect(widget.device.address).catchError((err) => setState(() => error = err))
                  : FlutterNfcAcs.disconnect(),
            ),
            Text(widget.device.name ?? 'No name'),
            StreamBuilder<int>(
              stream: FlutterNfcAcs.batteryStatus,
              builder: (context, snapshot) {
                if (snapshot.connectionState == ConnectionState.active)
                  return Text('Battery level: ' + snapshot.data.toString());
                else
                  return const SizedBox.shrink();
              },
            ),
            StreamBuilder<String>(
              stream: FlutterNfcAcs.cards,
              builder: (context, snapshot) {
                switch (snapshot.connectionState) {
                  case ConnectionState.none:
                    return Text('Card: no connection');
                  case ConnectionState.waiting:
                    return Text('Card: waiting');
                  case ConnectionState.active:
                    return Text('Card: ' + snapshot.data.toString());
                  case ConnectionState.done:
                    return Text('Card: done');
                  default:
                    return Text('Card: unknown state');
                }
              },
            ),
            StreamBuilder<String>(
              stream: FlutterNfcAcs.connectionStatus,
              builder: (context, snapshot) {
                switch (snapshot.connectionState) {
                  case ConnectionState.none:
                    return Text('Connection: nope');
                  case ConnectionState.waiting:
                    return Text('Connection: waiting');
                  case ConnectionState.active:
                    return Text('Connection: ' + snapshot.data.toString());
                  case ConnectionState.done:
                    return Text('Connection: done');
                  default:
                    return Text('Connection: unknown state');
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _sub?.cancel();
    FlutterNfcAcs.disconnect();
    super.dispose();
  }
}
