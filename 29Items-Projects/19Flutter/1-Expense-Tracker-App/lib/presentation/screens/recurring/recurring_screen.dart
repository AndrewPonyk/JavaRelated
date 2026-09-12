/// Recurring expenses management screen (subscriptions, bills, salaries).
library;

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../core/error/error_handler.dart';
import '../../../data/models/recurring_expense.dart';
import '../../providers/expense_provider.dart';
import '../../providers/recurring_provider.dart';
import '../../providers/settings_provider.dart';
import '../../widgets/async_value_view.dart';
import '../../widgets/expense_list_tile.dart' show categoryIconFor;

class RecurringScreen extends StatelessWidget {
  const RecurringScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<RecurringProvider>();
    final categories = {for (final c in context.watch<SettingsProvider>().categories) c.id: c};

    return Scaffold(
      appBar: AppBar(
        title: const Text('Recurring expenses'),
      ),
      body: AsyncValueView<List<RecurringExpense>>(
        state: provider.state,
        isEmpty: (list) => list.isEmpty,
        empty: (_) => const Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Text(
              'No recurring expenses yet.\nTap + to set up regular bills or subscriptions.',
              textAlign: TextAlign.center,
            ),
          ),
        ),
        data: (context, items) => ListView.builder(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
          itemCount: items.length,
          itemBuilder: (context, index) {
            final item = items[index];
            final category = categories[item.categoryId];
            final categoryColor = category != null ? Color(category.colorValue) : Colors.grey;

            return Dismissible(
              key: ValueKey(item.id),
              direction: DismissDirection.endToStart,
              background: Container(
                alignment: Alignment.centerRight,
                padding: const EdgeInsets.symmetric(horizontal: 24),
                color: Theme.of(context).colorScheme.errorContainer,
                child: Icon(Icons.delete_outline, color: Theme.of(context).colorScheme.onErrorContainer),
              ),
              confirmDismiss: (_) => _confirmDelete(context),
              onDismissed: (_) async {
                await provider.delete(item.id);
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Recurring expense deleted')),
                  );
                }
              },
              child: Card(
                margin: const EdgeInsets.only(bottom: 12),
                child: ListTile(
                  leading: CircleAvatar(
                    backgroundColor: categoryColor.withValues(alpha: 0.15),
                    child: Icon(
                      categoryIconFor(category?.iconCodePoint ?? 0xe5d3),
                      color: categoryColor,
                    ),
                  ),
                  title: Text(
                    item.note?.isNotEmpty ?? false ? item.note! : (category?.name ?? 'Recurring expense'),
                    style: TextStyle(
                      decoration: item.isActive ? null : TextDecoration.lineThrough,
                      color: item.isActive ? null : Theme.of(context).disabledColor,
                    ),
                  ),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: Theme.of(context).colorScheme.surfaceContainerHighest,
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              item.frequency.label,
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            'Starts ${DateFormat.yMMMd().format(item.startDate)}',
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ],
                      ),
                      if (item.lastGeneratedDate != null) ...[
                        const SizedBox(height: 2),
                        Text(
                          'Last logged: ${DateFormat.yMMMd().format(item.lastGeneratedDate!)}',
                          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                color: Theme.of(context).hintColor,
                              ),
                        ),
                      ],
                    ],
                  ),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        item.amount.formatted,
                        style: Theme.of(context).textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: item.isActive ? null : Theme.of(context).disabledColor,
                            ),
                      ),
                      const SizedBox(width: 8),
                      Switch(
                        value: item.isActive,
                        onChanged: (v) async {
                          final failure = await provider.toggleActive(item.id, v);
                          if (failure != null && context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(content: Text(ErrorHandler.userMessage(failure))),
                            );
                          } else if (context.mounted && v) {
                            // Reload month expenses in case any newly activated items caught up
                            context.read<ExpenseProvider>().load();
                          }
                        },
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        ),
      ),
      floatingActionButton: FloatingActionButton(
        heroTag: null,
        onPressed: () => _showAddSheet(context),
        child: const Icon(Icons.add),
      ),
    );
  }

  Future<bool> _confirmDelete(BuildContext context) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete recurring expense?'),
        content: const Text(
          'Future occurrences will not be generated. Previously generated expenses will remain.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Delete')),
        ],
      ),
    );
    return result ?? false;
  }

  void _showAddSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (_) => const AddRecurringExpenseSheet(),
    );
  }
}

class AddRecurringExpenseSheet extends StatefulWidget {
  const AddRecurringExpenseSheet({super.key});

  @override
  State<AddRecurringExpenseSheet> createState() => _AddRecurringExpenseSheetState();
}

class _AddRecurringExpenseSheetState extends State<AddRecurringExpenseSheet> {
  final _amountController = TextEditingController();
  final _currencyController = TextEditingController();
  final _noteController = TextEditingController();
  String? _categoryId;
  RecurrenceFrequency _frequency = RecurrenceFrequency.monthly;
  DateTime _startDate = DateTime.now();
  DateTime? _endDate;
  bool _hasEndDate = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final settings = context.read<SettingsProvider>().settings;
    _currencyController.text = settings.baseCurrency;
  }

  @override
  void dispose() {
    _amountController.dispose();
    _currencyController.dispose();
    _noteController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final categories = context.watch<SettingsProvider>().categories;

    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      child: ListView(
        shrinkWrap: true,
        children: [
          Text('New recurring expense', style: Theme.of(context).textTheme.titleLarge),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                flex: 2,
                child: TextField(
                  controller: _amountController,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'Amount'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: TextField(
                  controller: _currencyController,
                  maxLength: 3,
                  textCapitalization: TextCapitalization.characters,
                  decoration: const InputDecoration(labelText: 'Currency', counterText: ''),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            initialValue: _categoryId,
            decoration: const InputDecoration(labelText: 'Category'),
            items: [
              for (final c in categories) DropdownMenuItem(value: c.id, child: Text(c.name)),
            ],
            onChanged: (v) => setState(() => _categoryId = v),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<RecurrenceFrequency>(
            initialValue: _frequency,
            decoration: const InputDecoration(labelText: 'Frequency'),
            items: [
              for (final f in RecurrenceFrequency.values) DropdownMenuItem(value: f, child: Text(f.label)),
            ],
            onChanged: (v) {
              if (v != null) setState(() => _frequency = v);
            },
          ),
          const SizedBox(height: 12),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('First date'),
            subtitle: Text(DateFormat.yMMMd().format(_startDate)),
            trailing: const Icon(Icons.calendar_today_outlined),
            onTap: () async {
              final picked = await showDatePicker(
                context: context,
                initialDate: _startDate,
                firstDate: DateTime(2000),
                lastDate: DateTime(2100),
              );
              if (picked != null) setState(() => _startDate = picked);
            },
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Set end date'),
            value: _hasEndDate,
            onChanged: (v) => setState(() {
              _hasEndDate = v;
              if (!v) _endDate = null;
              if (v && _endDate == null) {
                _endDate = _startDate.add(const Duration(days: 365));
              }
            }),
          ),
          if (_hasEndDate)
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('End date'),
              subtitle: Text(_endDate != null ? DateFormat.yMMMd().format(_endDate!) : 'Select date'),
              trailing: const Icon(Icons.calendar_today_outlined),
              onTap: () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: _endDate ?? _startDate.add(const Duration(days: 30)),
                  firstDate: _startDate,
                  lastDate: DateTime(2100),
                );
                if (picked != null) setState(() => _endDate = picked);
              },
            ),
          const SizedBox(height: 12),
          TextField(
            controller: _noteController,
            decoration: const InputDecoration(labelText: 'Note (e.g. Netflix, Rent)'),
          ),
          const SizedBox(height: 24),
          FilledButton(
            onPressed: _save,
            child: const Text('Save recurring expense'),
          ),
        ],
      ),
    );
  }

  Future<void> _save() async {
    setState(() => _error = null);

    final failure = await context.read<RecurringProvider>().addRecurring(
          amountInput: _amountController.text.trim(),
          currencyCode: _currencyController.text.trim().toUpperCase(),
          categoryId: _categoryId ?? '',
          frequency: _frequency,
          startDate: _startDate,
          note: _noteController.text,
          endDate: _hasEndDate ? _endDate : null,
        );

    if (!mounted) return;

    if (failure != null) {
      setState(() => _error = ErrorHandler.userMessage(failure));
      return;
    }

    // Refresh expenses tab to reflect any newly materialized occurrences
    context.read<ExpenseProvider>().load();
    Navigator.of(context).pop();
  }
}
