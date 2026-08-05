import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

// Demo: parameter name hints + variable / lambda type hints
// 演示：参数名提示 + 局部变量 / lambda 类型提示
final ValueNotifier<String> demoListenable = ValueNotifier<String>('hello');

void greet(String name, int age) {}

class HintsDemoPage extends StatelessWidget {
  const HintsDemoPage({super.key});

  @override
  Widget build(BuildContext context) {
    // Type hints on locals without explicit types
    // 无显式类型的局部变量 → 类型提示
    final title = 'FlutterHelper';
    var count = 42;

    // Parameter name hints on positional args
    // 位置参数 → 参数名提示（name: / age:）
    greet('Ada', 30);

    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: ValueListenableBuilder<String>(
        valueListenable: demoListenable,
        builder: (context, value, child) {
          // Type hints on lambda parameters
          // lambda 形参 → 类型提示（BuildContext / String / Widget?）
          return Center(
            child: Text('$title · $count · $value'),
          );
        },
      ),
    );
  }
}
