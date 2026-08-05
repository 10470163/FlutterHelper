import 'package:flutter/material.dart';
import 'package:flutter_helper_screenshot_demo/hints_demo.dart';
import 'package:flutter_helper_screenshot_demo/trailing_comma_demo.dart';
import 'package:provider/provider.dart';

void main() {
  runApp(
    ChangeNotifierProvider(
      create: (_) => DemoCounter(),
      child: const ScreenshotDemoApp(),
    ),
  );
}

class ScreenshotDemoApp extends StatelessWidget {
  const ScreenshotDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FlutterHelper Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF0175C2)),
        useMaterial3: true,
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF0175C2),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      themeMode: ThemeMode.dark,
      home: const HomePage(),
    );
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('FlutterHelper Demo')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          ListTile(
            title: const Text('hints_demo.dart'),
            subtitle: const Text('Parameter / type hints'),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const HintsDemoPage()),
            ),
          ),
          ListTile(
            title: const Text('trailing_comma_demo.dart'),
            subtitle: const Text('Trailing commas / Extract Widget'),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const TrailingCommaDemoPage()),
            ),
          ),
          const Divider(),
          Text(
            'count: ${context.watch<DemoCounter>().value}',
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => context.read<DemoCounter>().increment(),
        child: const Icon(Icons.add),
      ),
    );
  }
}

class DemoCounter extends ChangeNotifier {
  int value = 0;

  void increment() {
    value++;
    notifyListeners();
  }
}
