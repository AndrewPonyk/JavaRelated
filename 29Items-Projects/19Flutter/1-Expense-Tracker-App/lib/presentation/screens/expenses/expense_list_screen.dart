/// Month-scoped expense list.
///
/// Deliberately does NOT show a running total of the raw list: expenses can be
/// in different currencies (multi-currency/traveler support), and
/// `Money.+`/`Money.sum` throw on a currency mismatch by design
/// (`core/utils/money.dart`). Any true total belongs to `AnalyticsProvider`,
/// which already converts everything to the base currency first.
library;

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../core/config/app_config.dart';
import '../../../core/router/app_router.dart';
import '../../../core/utils/date_range.dart';
import '../../../data/models/expense.dart';
import '../../providers/expense_provider.dart';
import '../../providers/settings_provider.dart';
import '../../widgets/async_value_view.dart';
import '../../widgets/expense_list_tile.dart';
import '../../widgets/quick_add_sheet.dart';

class ExpenseListScreen extends StatefulWidget {
  const ExpenseListScreen({super.key});

  @override
  State<ExpenseListScreen> createState() => _ExpenseListScreenState();
}

class _ExpenseListScreenState extends State<ExpenseListScreen> {
  bool _isSearching = false;
  final _searchController = TextEditingController();
  String _searchQuery = '';
  String? _selectedCategoryId;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ExpenseProvider>();
    final settingsProvider = context.watch<SettingsProvider>();
    final baseCurrency = settingsProvider.settings.baseCurrency;
    final categories = {for (final c in settingsProvider.categories) c.id: c};
    final monthLabel = DateFormat.yMMMM().format(provider.month);

    return Scaffold(
      appBar: AppBar(
        title: _isSearching
            ? TextField(
                controller: _searchController,
                autofocus: true,
                decoration: const InputDecoration(
                  hintText: 'Search note, category, amount...',
                  border: InputBorder.none,
                ),
                onChanged: (q) => setState(() => _searchQuery = q.trim().toLowerCase()),
              )
            : Text(monthLabel),
        leading: _isSearching
            ? IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: () {
                  setState(() {
                    _isSearching = false;
                    _searchQuery = '';
                    _searchController.clear();
                  });
                },
              )
            : IconButton(
                icon: const Icon(Icons.chevron_left),
                onPressed: provider.goToPreviousMonth,
              ),
        actions: [
          if (!_isSearching) ...[
            IconButton(
              icon: const Icon(Icons.search),
              tooltip: 'Search',
              onPressed: () => setState(() => _isSearching = true),
            ),
            IconButton(icon: const Icon(Icons.chevron_right), onPressed: provider.goToNextMonth),
            IconButton(
              icon: const Icon(Icons.flash_on_outlined),
              tooltip: 'Quick add',
              onPressed: () => showQuickAddSheet(context),
            ),
          ] else
            IconButton(
              icon: const Icon(Icons.clear),
              onPressed: () {
                _searchController.clear();
                setState(() => _searchQuery = '');
              },
            ),
        ],
      ),
      body: AsyncValueView<List<Expense>>(
        state: provider.state,
        isEmpty: (list) => list.isEmpty,
        empty: (_) => const Center(child: Text('No expenses logged this month.')),
        data: (context, allExpenses) {
          // Identify any currencies in use with stale FX rates
          final staleCurrencies = <String>{};
          for (final e in allExpenses) {
            if (e.currencyCode != baseCurrency && settingsProvider.isCurrencyStale(e.currencyCode)) {
              staleCurrencies.add(e.currencyCode);
            }
          }

          // Apply search and category filter
          final filtered = allExpenses.where((e) {
            if (_selectedCategoryId != null && e.categoryId != _selectedCategoryId) {
              return false;
            }
            if (_searchQuery.isNotEmpty) {
              final catName = categories[e.categoryId]?.name.toLowerCase() ?? '';
              final note = (e.note ?? '').toLowerCase();
              final amountStr = e.amount.decimalString;
              if (!catName.contains(_searchQuery) &&
                  !note.contains(_searchQuery) &&
                  !amountStr.contains(_searchQuery)) {
                return false;
              }
            }
            return true;
          }).toList();

          final byDay = <DateTime, List<Expense>>{};
          for (final e in filtered) {
            byDay.putIfAbsent(dateOnly(e.date), () => []).add(e);
          }
          final days = byDay.keys.toList()..sort((a, b) => b.compareTo(a));

          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Category filter chips
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                child: Row(
                  children: [
                    FilterChip(
                      label: const Text('All categories'),
                      selected: _selectedCategoryId == null,
                      onSelected: (_) => setState(() => _selectedCategoryId = null),
                    ),
                    const SizedBox(width: 8),
                    for (final cat in settingsProvider.categories) ...[
                      FilterChip(
                        label: Text(cat.name),
                        selected: _selectedCategoryId == cat.id,
                        onSelected: (selected) =>
                            setState(() => _selectedCategoryId = selected ? cat.id : null),
                      ),
                      const SizedBox(width: 8),
                    ],
                  ],
                ),
              ),

              // Stale FX rate banner
              if (staleCurrencies.isNotEmpty)
                Container(
                  margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.tertiaryContainer.withValues(alpha: 0.5),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Theme.of(context).colorScheme.tertiary.withValues(alpha: 0.3)),
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.schedule, size: 18, color: Theme.of(context).colorScheme.tertiary),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Exchange rates for ${staleCurrencies.join(', ')} are older than 24h.',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ),
                      if (AppConfig.liveFxRatesEnabled)
                        TextButton(
                          onPressed: () => context.read<SettingsProvider>().refreshRates(),
                          child: const Text('Refresh'),
                        ),
                    ],
                  ),
                ),

              Expanded(
                child: filtered.isEmpty
                    ? const Center(child: Text('No expenses match the current filter.'))
                    : ListView.builder(
                        padding: const EdgeInsets.only(bottom: 96),
                        itemCount: days.length,
                        itemBuilder: (context, index) {
                          final day = days[index];
                          final items = byDay[day]!;
                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Padding(
                                padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
                                child: Text(
                                  DateFormat.MMMEd().format(day),
                                  style: Theme.of(context).textTheme.labelLarge,
                                ),
                              ),
                              for (final expense in items)
                                ExpenseListTile(
                                  expense: expense,
                                  category: categories[expense.categoryId],
                                  onTap: () => Navigator.of(context).pushNamed(
                                    AppRoutes.addExpense,
                                    arguments: AddExpenseArgs(expense: expense),
                                  ),
                                  onDismissed: () => provider.deleteExpense(expense.id),
                                ),
                            ],
                          );
                        },
                      ),
              ),
            ],
          );
        },
      ),
    );
  }
}
