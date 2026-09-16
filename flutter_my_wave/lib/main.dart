import 'package:flutter/material.dart';

import 'my_wave_background.dart';

/// Minimal harness: a full-screen wave with a play/pause toggle, a BPM slider and a
/// manual amplitude, i.e. every uniform the shader accepts.
void main() => runApp(const MyWaveDemoApp());

class MyWaveDemoApp extends StatelessWidget {
  const MyWaveDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark(),
      home: const MyWaveDemoPage(),
    );
  }
}

class MyWaveDemoPage extends StatefulWidget {
  const MyWaveDemoPage({super.key});

  @override
  State<MyWaveDemoPage> createState() => _MyWaveDemoPageState();
}

class _MyWaveDemoPageState extends State<MyWaveDemoPage> {
  bool _connectedToPlayer = false;
  double _bpm = 120;
  double _amplitude = 0.5;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: MyWaveBackground(
        bpm: _bpm,
        // With no player attached the widget synthesises its own breathing amplitude.
        amplitude: _connectedToPlayer ? _amplitude : null,
        testAmplitude: !_connectedToPlayer,
        child: SafeArea(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.end,
            children: <Widget>[
              const Text(
                'Моя волна',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 34,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 24),
              SwitchListTile(
                value: _connectedToPlayer,
                onChanged: (bool value) => setState(() => _connectedToPlayer = value),
                title: const Text('Подключён плеер (ручная амплитуда)'),
              ),
              ListTile(
                title: Text('BPM ${_bpm.round()}'),
                subtitle: Slider(
                  value: _bpm,
                  min: 60,
                  max: 200,
                  onChanged: (double value) => setState(() => _bpm = value),
                ),
              ),
              ListTile(
                title: Text('Амплитуда ${_amplitude.toStringAsFixed(2)}'),
                subtitle: Slider(
                  value: _amplitude,
                  onChanged: _connectedToPlayer
                      ? (double value) => setState(() => _amplitude = value)
                      : null,
                ),
              ),
              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    );
  }
}
