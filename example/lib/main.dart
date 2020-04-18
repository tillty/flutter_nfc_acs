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
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Flutter NFC ACS'),
        ),
        body: StreamBuilder<List<AcsDevice>>(
          stream: FlutterNfcAcs.devices,
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              print(snapshot.error);
              return Text('Error');
            } else if (snapshot.hasData) {
              return ListView.builder(
                itemCount: snapshot.data.length,
                itemBuilder: (context, i) {
                  final item = snapshot.data[i];
                  return RaisedButton(
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
}

class DeviceRoute extends StatefulWidget {
  const DeviceRoute({Key key, @required this.device}) : super(key: key);

  final AcsDevice device;

  @override
  _DeviceRouteState createState() => _DeviceRouteState();
}

class _DeviceRouteState extends State<DeviceRoute> {
  String connection = FlutterNfcAcs.DISCONNECTED;
  String error;
  StreamSubscription _sub;

  @override
  void initState() {
    super.initState();

    FlutterNfcAcs.connect(widget.device).catchError((err) => setState(() => error = err));

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
            RaisedButton(
              child: Text(connection == FlutterNfcAcs.DISCONNECTED ? 'Connect' : 'Disconnect'),
              onPressed: () => connection == FlutterNfcAcs.DISCONNECTED
                  ? FlutterNfcAcs.connect(widget.device).catchError((err) => setState(() => error = err))
                  : FlutterNfcAcs.disconnect(),
            ),
            Text(widget.device?.name ?? 'No name'),
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
                    break;
                  case ConnectionState.waiting:
                    return Text('Card: waiting');
                    break;
                  case ConnectionState.active:
                    return Text('Card: ' + snapshot.data.toString());
                    break;
                  case ConnectionState.done:
                    return Text('Card: done');
                    break;
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
                    break;
                  case ConnectionState.waiting:
                    return Text('Connection: waiting');
                    break;
                  case ConnectionState.active:
                    return Text('Connection: ' + snapshot.data.toString());
                    break;
                  case ConnectionState.done:
                    return Text('Connection: done');
                    break;
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
