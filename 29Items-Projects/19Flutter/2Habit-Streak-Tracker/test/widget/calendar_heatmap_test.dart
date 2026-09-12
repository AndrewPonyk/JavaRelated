import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/features/analytics/presentation/widgets/calendar_heatmap.dart';

void main() {
  testWidgets(
    'CalendarHeatmap renders title, cells, legend and triggers callback on tap',
    (tester) async {
      String? tappedDate;
      int? tappedCount;

      final counts = {
        '2026-09-06': 5,
        '2026-09-05': 3,
        '2026-09-04': 2,
        '2026-09-03': 1,
        '2026-09-02': 0,
      };

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: CalendarHeatmap(
              dailyCounts: counts,
              daysToShow: 14,
              onDayTap: (date, count) {
                tappedDate = date;
                tappedCount = count;
              },
            ),
          ),
        ),
      );

      expect(find.text('Activity Heatmap'), findsOneWidget);
      expect(find.text('Less'), findsOneWidget);
      expect(find.text('More'), findsOneWidget);

      // Tap first cell
      final gestureDetectors = find.byType(GestureDetector);
      expect(gestureDetectors, findsWidgets);

      await tester.tap(gestureDetectors.first);
      await tester.pump();

      expect(tappedDate, isNotNull);
      expect(tappedCount, isNotNull);
    },
  );
}
