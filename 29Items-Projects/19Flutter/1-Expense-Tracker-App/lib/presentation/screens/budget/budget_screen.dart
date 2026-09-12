/// Month-scoped budget list: one overall row plus one row per category with
/// a budget set. Editing and deleting share a single bottom-sheet form.
library;

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../core/error/error_handler.dart';
import '../../../data/models/budget.dart';
import '../../../domain/services/budget_service.dart';
import '../../providers/budget_provider.dart';
import '../../providers/settings_provider.dart';
import '../../widgets/async_value_view.dart';
import '../../widgets/budget_progress_bar.dart';

class BudgetScreen extends StatelessWidget {
  const BudgetScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<BudgetProvider>();
    final categories = {for (final c in context.watch<SettingsProvider>().categories) c.id: c.name};
    final monthLabel = DateFormat.yMMMM().format(provider.month);

    return Scaffold(
      appBar: AppBar(
        title: Text(monthLabel),
        leading: IconButton(icon: const Icon(Icons.chevron_left), onPressed: provider.goToPreviousMonth),
        actions: [
          IconButton(icon: const Icon(Icons.chevron_right), onPressed: provider.goToNextMonth),
          IconButton(
            icon: const Icon(Icons.content_copy_outlined),
            tooltip: 'Copy last month\'s budgets',
            onPressed: () => _copyFromPreviousMonth(context),
          ),
        ],
      ),
      body: AsyncValueView<List<BudgetProgress>>(
        state: provider.state,
        isEmpty: (list) => list.isEmpty,
        empty: (_) => const Center(child: Text('No budgets set for this month yet.')),
        data: (context, progressList) => ListView.builder(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
          itemCount: progressList.length,
          itemBuilder: (context, index) {
            final progress = progressList[index];
            return Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: BudgetProgressBar(
                progress: progress,
                label: progress.budget.isOverall ? 'Overall' : categories[progress.budget.categoryId],
                onTap: () => showBudgetFormSheet(context, existing: progress.budget),
              ),
            );
          },
        ),
      ),
      floatingActionButton: FloatingActionButton(
        heroTag: null,
        onPressed: () => showBudgetFormSheet(context),
        child: const Icon(Icons.add),
      ),
    );
  }

  Future<void> _copyFromPreviousMonth(BuildContext context) async {
    final failure = await context.read<BudgetProvider>().copyFromPreviousMonth();
    if (failure != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ErrorHandler.userMessage(failure))));
    }
  }
}

Future<void> showBudgetFormSheet(BuildContext context, {Budget? existing}) {
  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    builder: (context) => BudgetFormSheet(existing: existing),
  );
}

class BudgetFormSheet extends StatefulWidget {
  const BudgetFormSheet({super.key, this.existing});

  final Budget? existing;

  @override
  State<BudgetFormSheet> createState() => _BudgetFormSheetState();
}

class _BudgetFormSheetState extends State<BudgetFormSheet> {
  late final TextEditingController _limitController;
  late final TextEditingController _currencyController;
  String? _categoryId;
  double _alertThreshold = 0.8;
  String? _error;

  @override
  void initState() {
    super.initState();
    final existing = widget.existing;
    _limitController = TextEditingController(text: existing?.limit.decimalString ?? '');
    _currencyController = TextEditingController(
      text: existing?.currencyCode ?? context.read<SettingsProvider>().settings.baseCurrency,
    );
    _categoryId = existing?.categoryId;
    _alertThreshold = existing?.alertThreshold ?? 0.8;
  }

  @override
  void dispose() {
    _limitController.dispose();
    _currencyController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final categories = context.watch<SettingsProvider>().categories;
    final isEditing = widget.existing != null;

    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(16, 16, 16, 16 + MediaQuery.of(context).viewInsets.bottom),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(isEditing ? 'Edit budget' : 'Set a budget', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 16),
            DropdownButtonFormField<String?>(
              initialValue: _categoryId,
              decoration: const InputDecoration(labelText: 'Category (leave blank for overall)'),
              items: [
                const DropdownMenuItem<String?>(value: null, child: Text('Overall (whole month)')),
                for (final c in categories) DropdownMenuItem<String?>(value: c.id, child: Text(c.name)),
              ],
              onChanged: isEditing ? null : (v) => setState(() => _categoryId = v),
            ),
            const SizedBox(height: 12),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  flex: 2,
                  child: TextFormField(
                    controller: _limitController,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(labelText: 'Monthly limit', errorText: _error),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextFormField(
                    controller: _currencyController,
                    textCapitalization: TextCapitalization.characters,
                    maxLength: 3,
                    decoration: const InputDecoration(labelText: 'Currency', counterText: ''),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text('Alert at ${(_alertThreshold * 100).round()}% of limit', style: Theme.of(context).textTheme.bodySmall),
            Slider(
              value: _alertThreshold,
              min: 0.5,
              max: 1.0,
              divisions: 10,
              label: '${(_alertThreshold * 100).round()}%',
              onChanged: (v) => setState(() => _alertThreshold = v),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                if (isEditing)
                  TextButton(
                    onPressed: _delete,
                    style: TextButton.styleFrom(foregroundColor: Theme.of(context).colorScheme.error),
                    child: const Text('Delete'),
                  ),
                const Spacer(),
                FilledButton(onPressed: _save, child: const Text('Save')),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _save() async {
    setState(() => _error = null);
    final failure = await context.read<BudgetProvider>().setBudget(
          limitInput: _limitController.text,
          currencyCode: _currencyController.text.trim().toUpperCase(),
          categoryId: _categoryId,
          alertThreshold: _alertThreshold,
        );
    if (!mounted) return;
    if (failure != null) {
      setState(() => _error = ErrorHandler.userMessage(failure));
      return;
    }
    Navigator.of(context).pop();
  }

  Future<void> _delete() async {
    final failure = await context.read<BudgetProvider>().deleteBudget(categoryId: _categoryId);
    if (!mounted) return;
    if (failure != null) {
      setState(() => _error = ErrorHandler.userMessage(failure));
      return;
    }
    Navigator.of(context).pop();
  }
}
