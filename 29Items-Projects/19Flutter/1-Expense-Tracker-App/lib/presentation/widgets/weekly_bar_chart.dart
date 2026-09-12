/// Weekly spending bar chart for the analytics screen.
library;

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/theme/app_colors.dart';
import '../../domain/services/analytics_service.dart';

class WeeklyBarChart extends StatelessWidget {
  const WeeklyBarChart({super.key, required this.buckets, this.labelFormat});

  final List<BucketTotal> buckets;
  final String Function(DateTime date)? labelFormat;

  @override
  Widget build(BuildContext context) {
    if (buckets.isEmpty) {
      return const SizedBox(
        height: 160,
        child: Center(child: Text('No spending yet this period.')),
      );
    }

    final maxValue = buckets.map((b) => b.total.asDouble).fold<double>(0, (a, b) => a > b ? a : b);

    return SizedBox(
      height: 200,
      child: BarChart(
        BarChartData(
          maxY: maxValue == 0 ? 1 : maxValue * 1.2,
          barGroups: [
            for (var i = 0; i < buckets.length; i++)
              BarChartGroupData(
                x: i,
                barRods: [
                  BarChartRodData(
                    toY: buckets[i].total.asDouble,
                    color: AppColors.seed,
                    width: 18,
                    borderRadius: const BorderRadius.vertical(top: Radius.circular(4)),
                  ),
                ],
              ),
          ],
          gridData: const FlGridData(drawVerticalLine: false),
          borderData: FlBorderData(show: false),
          titlesData: FlTitlesData(
            leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                getTitlesWidget: (value, meta) {
                  final index = value.toInt();
                  if (index < 0 || index >= buckets.length) return const SizedBox.shrink();
                  final label = labelFormat != null
                      ? labelFormat!(buckets[index].range.start)
                      : DateFormat.Md().format(buckets[index].range.start);
                  return Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(
                      label,
                      style: Theme.of(context).textTheme.labelSmall,
                    ),
                  );
                },
              ),
            ),
          ),
          barTouchData: BarTouchData(
            touchTooltipData: BarTouchTooltipData(
              getTooltipItem: (group, groupIndex, rod, rodIndex) => BarTooltipItem(
                buckets[group.x].total.formatted,
                const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
