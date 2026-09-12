/// Month-scoped spending breakdown: total, category pie, weekly bar chart.
/// Purely read-only — all figures are already base-currency-converted by
/// [AnalyticsProvider]/[AnalyticsService].
library;

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../core/utils/money.dart';
import '../../../domain/services/analytics_service.dart';
import '../../providers/analytics_provider.dart';
import '../../providers/settings_provider.dart';
import '../../widgets/async_value_view.dart';
import '../../widgets/category_pie_chart.dart';
import '../../widgets/weekly_bar_chart.dart';

class AnalyticsScreen extends StatefulWidget {
  const AnalyticsScreen({super.key});

  @override
  State<AnalyticsScreen> createState() => _AnalyticsScreenState();
}

class _AnalyticsScreenState extends State<AnalyticsScreen> {
  int _trendIndex = 0;

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AnalyticsProvider>();
    final categories = {for (final c in context.watch<SettingsProvider>().categories) c.id: c};
    final monthLabel = DateFormat.yMMMM().format(provider.month);

    return Scaffold(
      appBar: AppBar(
        title: Text(monthLabel),
        leading: IconButton(icon: const Icon(Icons.chevron_left), onPressed: provider.goToPreviousMonth),
        actions: [
          IconButton(icon: const Icon(Icons.chevron_right), onPressed: provider.goToNextMonth),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Total spent', style: Theme.of(context).textTheme.labelLarge),
                  const SizedBox(height: 4),
                  AsyncValueView<Money>(
                    state: provider.total,
                    data: (context, total) => Text(
                      total.formatted,
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text('By category', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          AsyncValueView<List<CategoryTotal>>(
            state: provider.byCategory,
            isEmpty: (list) => list.isEmpty,
            empty: (_) => const Padding(
              padding: EdgeInsets.symmetric(vertical: 24),
              child: Center(child: Text('No spending yet this month.')),
            ),
            data: (context, totals) => CategoryPieChart(totals: totals, categories: categories),
          ),
          const SizedBox(height: 24),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                _trendIndex == 0 ? 'By week' : 'Monthly trend',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              SegmentedButton<int>(
                segments: const [
                  ButtonSegment(value: 0, label: Text('Weekly')),
                  ButtonSegment(value: 1, label: Text('Monthly')),
                ],
                selected: {_trendIndex},
                onSelectionChanged: (s) => setState(() => _trendIndex = s.first),
              ),
            ],
          ),
          const SizedBox(height: 8),
          if (_trendIndex == 0)
            AsyncValueView<List<BucketTotal>>(
              state: provider.byWeek,
              isEmpty: (list) => list.isEmpty,
              empty: (_) => const Padding(
                padding: EdgeInsets.symmetric(vertical: 24),
                child: Center(child: Text('No spending yet this month.')),
              ),
              data: (context, buckets) => WeeklyBarChart(buckets: buckets),
            )
          else
            AsyncValueView<List<BucketTotal>>(
              state: provider.byMonth,
              isEmpty: (list) => list.isEmpty,
              empty: (_) => const Padding(
                padding: EdgeInsets.symmetric(vertical: 24),
                child: Center(child: Text('No spending recorded for this period.')),
              ),
              data: (context, buckets) => WeeklyBarChart(
                buckets: buckets,
                labelFormat: (date) => DateFormat.MMM().format(date),
              ),
            ),
        ],
      ),
    );
  }
}
