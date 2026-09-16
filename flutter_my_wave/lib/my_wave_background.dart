import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

/// A full-bleed animated "My Wave" background.
///
/// The whole picture is computed by the fragment shader in
/// `assets/shaders/my_wave.frag` (simplex-noise deformation, an amplitude pulse and a
/// vignette), so nothing is drawn per-widget and no asset has to be decoded.
///
/// ```dart
/// const MyWaveBackground(
///   bpm: 124,                 // uBpmSpeed becomes 124 / 120
///   color1: Color(0xFF6B11FF),
///   color2: Color(0xFF00D2FF),
///   child: Center(child: Text('Моя волна')),
/// )
/// ```
class MyWaveBackground extends StatefulWidget {
  const MyWaveBackground({
    super.key,
    this.bpm = 120.0,
    this.amplitude,
    this.color1 = const Color(0xFF6B11FF),
    this.color2 = const Color(0xFF00D2FF),
    this.testAmplitude = true,
    this.child,
  });

  /// Tempo of the current track. `uBpmSpeed` is sent as `bpm / 120`, so 120 BPM is the
  /// neutral speed the shader was written around.
  final double bpm;

  /// Loudness, 0.0 … 1.0. When null (and [testAmplitude] is true) a floating sine wave
  /// is sent instead, which keeps the background alive without a player attached.
  final double? amplitude;

  /// Palette. `uColor1` is mixed towards `uColor2` by the noise field.
  final Color color1;
  final Color color2;

  /// When true and [amplitude] is null, an amplitude is synthesised from a sine wave.
  final bool testAmplitude;

  /// Drawn on top of the wave.
  final Widget? child;

  @override
  State<MyWaveBackground> createState() => _MyWaveBackgroundState();
}

class _MyWaveBackgroundState extends State<MyWaveBackground>
    with SingleTickerProviderStateMixin {
  ui.FragmentProgram? _program;

  /// Drives one repaint per frame. The value itself is unused — the elapsed time comes
  /// from the [Stopwatch], which never wraps and therefore never makes the field jump.
  late final AnimationController _ticker;

  final Stopwatch _clock = Stopwatch();

  @override
  void initState() {
    super.initState();
    _ticker = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 1),
    )..repeat();
    _clock.start();
    _load();
  }

  Future<void> _load() async {
    final ui.FragmentProgram program =
        await ui.FragmentProgram.fromAsset('assets/shaders/my_wave.frag');
    if (!mounted) return;
    setState(() => _program = program);
  }

  @override
  void dispose() {
    _ticker.dispose();
    _clock.stop();
    super.dispose();
  }

  double get _elapsedSeconds => _clock.elapsedMicroseconds / 1000000.0;

  double get _bpmSpeed {
    final double bpm = widget.bpm.isFinite && widget.bpm > 0 ? widget.bpm : 120.0;
    return bpm / 120.0;
  }

  double _amplitudeAt(double seconds) {
    final double? provided = widget.amplitude;
    if (provided != null) {
      return provided.isFinite ? provided.clamp(0.0, 1.0) : 0.0;
    }
    if (!widget.testAmplitude) return 0.0;
    // Test signal: a slow breathing pulse in 0.0 … 1.0.
    return (0.5 + 0.5 * math.sin(seconds * 2.0)).clamp(0.0, 1.0);
  }

  @override
  Widget build(BuildContext context) {
    final ui.FragmentProgram? program = _program;

    if (program == null) {
      // Until the shader is compiled, show the same palette as a plain gradient so the
      // screen never flashes black.
      return DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: <Color>[widget.color1, widget.color2],
          ),
        ),
        child: widget.child,
      );
    }

    // RepaintBoundary keeps the per-frame shader repaint inside its own layer, so the
    // rest of the tree is not repainted with it.
    return RepaintBoundary(
      child: CustomPaint(
        painter: _MyWavePainter(
          program: program,
          repaint: _ticker,
          elapsedSeconds: _elapsedSeconds,
          bpmSpeed: _bpmSpeed,
          amplitudeAt: _amplitudeAt,
          color1: widget.color1,
          color2: widget.color2,
        ),
        // CustomPaint needs a size when it has a child; fill the parent instead.
        size: Size.infinite,
        child: widget.child,
      ),
    );
  }
}

class _MyWavePainter extends CustomPainter {
  _MyWavePainter({
    required this.program,
    required Listenable repaint,
    required this.elapsedSeconds,
    required this.bpmSpeed,
    required this.amplitudeAt,
    required this.color1,
    required this.color2,
  }) : super(repaint: repaint);

  final ui.FragmentProgram program;

  /// Read inside [paint], so the time keeps moving between rebuilds.
  final double Function() elapsedSeconds;

  final double bpmSpeed;
  final double Function(double seconds) amplitudeAt;
  final Color color1;
  final Color color2;

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;

    final double seconds = elapsedSeconds();
    final ui.FragmentShader shader = program.fragmentShader();

    // Uniform order must match the shader exactly:
    //   vec2 uResolution | float uTime | float uBpmSpeed | float uAmplitude
    //   vec3 uColor1 | vec3 uColor2
    shader.setFloat(0, size.width);
    shader.setFloat(1, size.height);
    shader.setFloat(2, seconds);
    shader.setFloat(3, bpmSpeed);
    shader.setFloat(4, amplitudeAt(seconds));
    // NOTE: `.r/.g/.b` are 0.0…1.0 and exist from Flutter 3.27. On an older SDK use
    // `color1.red / 255.0`, `color1.green / 255.0`, `color1.blue / 255.0`.
    shader.setFloat(5, color1.r);
    shader.setFloat(6, color1.g);
    shader.setFloat(7, color1.b);
    shader.setFloat(8, color2.r);
    shader.setFloat(9, color2.g);
    shader.setFloat(10, color2.b);

    canvas.drawRect(
      Offset.zero & size,
      Paint()..shader = shader,
    );

    // FragmentShader is a native resource; letting it go every frame is what keeps the
    // memory flat on a long-running background.
    shader.dispose();
  }

  @override
  bool shouldRepaint(covariant _MyWavePainter oldDelegate) {
    return oldDelegate.program != program ||
        oldDelegate.color1 != color1 ||
        oldDelegate.color2 != color2 ||
        oldDelegate.bpmSpeed != bpmSpeed;
  }
}
