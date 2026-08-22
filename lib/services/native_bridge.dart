import 'package:flutter/services.dart';

class NativeBridge {
  static const MethodChannel _channel = MethodChannel('com.umbrella.app/native');

  static Future<void> startVpn() async {
    try {
      await _channel.invokeMethod('startVpn');
    } on PlatformException catch (e) {
      print("Failed to start VPN: '${e.message}'.");
    }
  }

  static Future<void> checkAccessibilityPermission() async {
    try {
      await _channel.invokeMethod('checkAccessibility');
    } on PlatformException catch (e) {
      print("Failed to check accessibility: '${e.message}'.");
    }
  }

  static Future<String> sendToLocalAI(String prompt) async {
    try {
      final String result = await _channel.invokeMethod('askLocalAI', {'prompt': prompt});
      return result;
    } on PlatformException catch (e) {
      return "Error communicating with AI: ${e.message}";
    }
  }
}
