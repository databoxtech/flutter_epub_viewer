import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:r2_viewer/r2_viewer.dart';

void main() {
  const MethodChannel channel = MethodChannel('r2_viewer');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await R2Viewer.platformVersion, '42');
  });
}
