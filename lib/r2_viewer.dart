import 'dart:async';

import 'package:flutter/services.dart';

class R2Viewer {
  static const MethodChannel _channel = const MethodChannel('r2_viewer');

  static Future openEpub(String bookPath, int bookId) async {
    Map<String, dynamic> agrs = {"bookPath": bookPath, "bookId": bookId};
    await _channel.invokeMethod('openEpub', agrs);
  }
}
