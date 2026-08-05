import 'package:flutter/material.dart';

// Demo: trailing commas on multi-line calls / lists; Extract Widget selection target
// 演示：多行调用 / 列表尾随逗号；也可作为「提取 Widget」选区目标
class TrailingCommaDemoPage extends StatelessWidget {
  const TrailingCommaDemoPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Trailing comma demo')),
      // Multi-line Column / children → trailing commas on reformat
      // 多行 Column / children → 格式化时补尾随逗号
      body: Column(
        children: [
          Text('a'),
          Text('b'),
          ElevatedButton(
            onPressed: () {},
            child: const Text('ok'),
          ),
        ],
      ),
    );
  }
}

// Demo: setState should NOT get forced trailing commas
// 演示：setState 不应被强制补尾随逗号
class TrailingCommaStateDemo extends StatefulWidget {
  const TrailingCommaStateDemo({super.key});

  @override
  State<TrailingCommaStateDemo> createState() => _TrailingCommaStateDemoState();
}

class _TrailingCommaStateDemoState extends State<TrailingCommaStateDemo> {
  int n = 0;

  @override
  Widget build(BuildContext context) {
    return TextButton(
      onPressed: () {
        setState(() {
          n++;
        });
      },
      child: Text('$n'),
    );
  }
}
