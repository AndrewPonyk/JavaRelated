import 'package:flutter/material.dart';

import 'features/pois/presentation/poi_list_screen.dart';

void main() {
  runApp(const IndoorMappingApp());
}

class IndoorMappingApp extends StatelessWidget {
  const IndoorMappingApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Indoor Mapping',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
        useMaterial3: true,
      ),
      home: const PoiListScreen(),
    );
  }
}
