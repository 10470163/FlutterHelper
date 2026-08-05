import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_helper_screenshot_demo/main.dart';
import 'package:provider/provider.dart';

void main() {
  testWidgets('home shows title', (tester) async {
    await tester.pumpWidget(
      ChangeNotifierProvider(
        create: (_) => DemoCounter(),
        child: const ScreenshotDemoApp(),
      ),
    );
    expect(find.text('FlutterHelper Demo'), findsOneWidget);
  });
}
