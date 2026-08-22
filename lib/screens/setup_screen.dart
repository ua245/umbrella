import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SetupScreen extends StatefulWidget {
  @override
  _SetupScreenState createState() => _SetupScreenState();
}

class _SetupScreenState extends State<SetupScreen> {
  final _storage = const FlutterSecureStorage();
  final _apiKeyController = TextEditingController();

  void _onDetect(BarcodeCapture capture) async {
    final List<Barcode> barcodes = capture.barcodes;
    if (barcodes.isNotEmpty) {
      final code = barcodes.first.rawValue;
      if (code != null) {
        // Expected format: family_id::parent_public_key
        final parts = code.split('::');
        if (parts.length == 2) {
          await _storage.write(key: 'family_id', value: parts[0]);
          await _storage.write(key: 'parent_public_key', value: parts[1]);
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Handshake Successful!')),
            );
          }
        }
      }
    }
  }

  void _saveApiKey() async {
    await _storage.write(key: 'api_key', value: _apiKeyController.text);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('API Key Saved!')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Umbrella Setup')),
      body: Column(
        children: [
          Expanded(
            flex: 2,
            child: MobileScanner(
              onDetect: _onDetect,
            ),
          ),
          Expanded(
            flex: 1,
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                children: [
                  const Text('Or Enter API Key Seamlessly:'),
                  TextField(
                    controller: _apiKeyController,
                    decoration: InputDecoration(
                      labelText: 'API Key',
                      suffixIcon: IconButton(
                        icon: const Icon(Icons.save),
                        onPressed: _saveApiKey,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
