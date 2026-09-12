/// Add/edit form. Edit mode is driven entirely by [AddExpenseScreen.args]
/// carrying an existing [Expense] — there is no separate edit route
/// (`core/router/app_router.dart`).
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/error/error_handler.dart';
import '../../../core/error/failures.dart';
import '../../../core/router/app_router.dart';
import '../../../data/models/expense.dart';
import '../../../data/repositories/expense_repository.dart';
import '../../../data/repositories/template_repository.dart';
import '../../providers/expense_provider.dart';
import '../../providers/settings_provider.dart';

class AddExpenseScreen extends StatefulWidget {
  const AddExpenseScreen({super.key, required this.args});

  final AddExpenseArgs args;

  @override
  State<AddExpenseScreen> createState() => _AddExpenseScreenState();
}

class _AddExpenseScreenState extends State<AddExpenseScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _amountController;
  late final TextEditingController _currencyController;
  late final TextEditingController _noteController;
  late DateTime _date;
  String? _categoryId;
  String? _amountError;
  String? _currencyError;
  String? _categoryError;

  Expense? get _editing => widget.args.expense;
  bool get _isEditing => _editing != null;

  @override
  void initState() {
    super.initState();
    final existing = _editing;
    final defaultCurrency = context.read<SettingsProvider>().settings.baseCurrency;

    _amountController = TextEditingController(text: existing?.amount.decimalString ?? '');
    _currencyController = TextEditingController(text: existing?.currencyCode ?? defaultCurrency);
    _noteController = TextEditingController(text: existing?.note ?? '');
    _date = existing?.date ?? DateTime.now();
    _categoryId = existing?.categoryId;
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

    return Scaffold(
      appBar: AppBar(title: Text(_isEditing ? 'Edit expense' : 'Add expense')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  flex: 2,
                  child: TextFormField(
                    controller: _amountController,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(labelText: 'Amount', errorText: _amountError),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextFormField(
                    controller: _currencyController,
                    textCapitalization: TextCapitalization.characters,
                    maxLength: 3,
                    decoration: InputDecoration(
                      labelText: 'Currency',
                      errorText: _currencyError,
                      counterText: '',
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: _categoryId,
              decoration: InputDecoration(labelText: 'Category', errorText: _categoryError),
              items: [
                for (final c in categories) DropdownMenuItem(value: c.id, child: Text(c.name)),
              ],
              onChanged: (v) => setState(() => _categoryId = v),
            ),
            const SizedBox(height: 12),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Date'),
              subtitle: Text('${_date.year}-${_date.month.toString().padLeft(2, '0')}-${_date.day.toString().padLeft(2, '0')}'),
              trailing: const Icon(Icons.calendar_today_outlined),
              onTap: _pickDate,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _noteController,
              maxLength: 500,
              maxLines: 2,
              decoration: const InputDecoration(labelText: 'Note (optional)'),
            ),
            const SizedBox(height: 24),
            FilledButton(
              onPressed: _save,
              child: Text(_isEditing ? 'Save changes' : 'Add expense'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(1970),
      lastDate: DateTime.now().add(const Duration(days: 366)),
    );
    if (picked != null) setState(() => _date = picked);
  }

  Future<void> _save() async {
    setState(() {
      _amountError = null;
      _currencyError = null;
      _categoryError = null;
    });

    final draft = ExpenseDraft(
      id: _editing?.id,
      amountInput: _amountController.text,
      currencyCode: _currencyController.text.trim().toUpperCase(),
      categoryId: _categoryId ?? '',
      date: _date,
      note: _noteController.text,
      recurringId: _editing?.recurringId,
    );

    final provider = context.read<ExpenseProvider>();
    final failure = _isEditing ? await provider.updateExpense(draft) : await provider.addExpense(draft);

    if (!mounted) return;

    if (failure != null) {
      setState(() {
        switch (failure) {
          case ValidationFailure(field: 'amount'):
            _amountError = ErrorHandler.userMessage(failure);
          case ValidationFailure(field: 'currency'):
            _currencyError = ErrorHandler.userMessage(failure);
          case ValidationFailure(field: 'category'):
            _categoryError = ErrorHandler.userMessage(failure);
          default:
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(ErrorHandler.userMessage(failure))),
            );
        }
      });
      return;
    }

    if (!_isEditing) await _offerSaveAsTemplate(draft);
    if (mounted) Navigator.of(context).pop();
  }

  /// Asked before popping, while this screen's context is still in the tree —
  /// declining costs the user nothing, and accepting turns a one-off entry
  /// into a quick-add tile.
  Future<void> _offerSaveAsTemplate(ExpenseDraft draft) async {
    final wantsTemplate = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Save as template?'),
        content: const Text('Add this expense to your quick-add templates.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('No thanks')),
          FilledButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Save as template')),
        ],
      ),
    );
    if (wantsTemplate == true && mounted) {
      await _showSaveTemplateDialog(context, draft);
    }
  }

  Future<void> _showSaveTemplateDialog(BuildContext context, ExpenseDraft draft) async {
    final controller = TextEditingController();
    final label = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Template name'),
        content: TextField(controller: controller, autofocus: true),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.of(context).pop(controller.text), child: const Text('Save')),
        ],
      ),
    );
    if (label == null || label.trim().isEmpty || !context.mounted) return;

    final result = await context.read<TemplateRepository>().create(
          label: label.trim(),
          amountInput: draft.amountInput,
          currencyCode: draft.currencyCode,
          categoryId: draft.categoryId,
        );
    if (result.failureOrNull != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ErrorHandler.userMessage(result.failureOrNull!))),
      );
    }
  }
}
