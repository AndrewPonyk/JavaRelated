/// Preferences, currency/FX, categories, and data management (export/erase).
/// Diagnostics tile is dev/staging-only ([AppConfig.showDiagnostics]).
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/config/app_config.dart';
import '../../../core/config/env.dart';
import '../../../core/error/error_handler.dart';
import '../../../core/router/app_router.dart';
import '../../providers/settings_provider.dart';
import '../../widgets/expense_list_tile.dart' show categoryIconFor;

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<SettingsProvider>();
    final settings = provider.settings;

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        children: [
          const _SectionHeader('Preferences'),
          SegmentedButton<int>(
            segments: const [
              ButtonSegment(value: 0, label: Text('System'), icon: Icon(Icons.brightness_auto)),
              ButtonSegment(value: 1, label: Text('Light'), icon: Icon(Icons.light_mode)),
              ButtonSegment(value: 2, label: Text('Dark'), icon: Icon(Icons.dark_mode)),
            ],
            selected: {settings.themeModeIndex},
            onSelectionChanged: (s) => provider.save(settings.copyWith(themeModeIndex: s.first)),
          ),
          SwitchListTile(
            title: const Text('Week starts on Monday'),
            value: settings.weekStartsOnMonday,
            onChanged: (v) => provider.save(settings.copyWith(weekStartsOnMonday: v)),
          ),
          SwitchListTile(
            title: const Text('Monthly rollover reminder'),
            subtitle: const Text('Notify at the start of each month to set a new budget'),
            value: settings.monthlyRolloverReminder,
            onChanged: (v) => provider.save(settings.copyWith(monthlyRolloverReminder: v)),
          ),
          SwitchListTile(
            title: const Text('Budget alert notifications'),
            subtitle: AppConfig.notificationsSupported
                ? null
                : const Text('Not supported on this platform'),
            value: settings.notificationsEnabled && AppConfig.notificationsSupported,
            onChanged: AppConfig.notificationsSupported
                ? (v) => _toggleNotifications(context, v)
                : null,
          ),
          ListTile(
            title: const Text('Default budget alert threshold'),
            subtitle: Slider(
              value: settings.budgetAlertThreshold,
              min: 0.5,
              max: 1.0,
              divisions: 10,
              label: '${(settings.budgetAlertThreshold * 100).round()}%',
              onChanged: (v) => provider.save(settings.copyWith(budgetAlertThreshold: v)),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.repeat),
            title: const Text('Recurring expenses'),
            subtitle: const Text('Subscriptions, regular bills, and income'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).pushNamed(AppRoutes.recurring),
          ),
          const Divider(),
          const _SectionHeader('Currency'),
          ListTile(
            title: const Text('Base currency'),
            subtitle: Text(settings.baseCurrency),
            trailing: const Icon(Icons.edit_outlined),
            onTap: () => _editBaseCurrency(context, settings.baseCurrency),
          ),
          if (AppConfig.liveFxRatesEnabled)
            ListTile(
              title: const Text('Refresh live rates'),
              trailing: const Icon(Icons.sync_outlined),
              onTap: () => _refreshRates(context),
            ),
          ListTile(
            leading: const Icon(Icons.add),
            title: const Text('Add manual exchange rate'),
            onTap: () => _addManualRate(context),
          ),
          for (final rate in provider.cachedRates)
            ListTile(
              dense: true,
              title: Text('${rate.code} → ${rate.baseCode}'),
              subtitle: Text(rate.isManual ? 'Manual rate' : 'Cached rate'),
              trailing: Text(rate.rateToBase.toStringAsFixed(4)),
            ),
          const Divider(),
          const _SectionHeader('Categories'),
          for (final category in provider.categories)
            ListTile(
              leading: Icon(categoryIconFor(category.iconCodePoint)),
              title: Text(category.name),
              trailing: category.isBuiltIn
                  ? null
                  : IconButton(
                      icon: const Icon(Icons.delete_outline),
                      onPressed: () => _deleteCategory(context, category.id),
                    ),
            ),
          ListTile(
            leading: const Icon(Icons.add),
            title: const Text('Add category'),
            onTap: () => _addCategory(context),
          ),
          const Divider(),
          const _SectionHeader('Data'),
          if (AppConfig.exportSupported)
            ListTile(
              leading: const Icon(Icons.share_outlined),
              title: const Text('Export expenses to CSV'),
              onTap: () => _exportCsv(context),
            ),
          ListTile(
            leading: Icon(Icons.delete_forever_outlined, color: Theme.of(context).colorScheme.error),
            title: Text('Erase all data', style: TextStyle(color: Theme.of(context).colorScheme.error)),
            onTap: () => _eraseAllData(context),
          ),
          if (AppConfig.showDiagnostics) ...[
            const Divider(),
            const _SectionHeader('Diagnostics'),
            for (final entry in Env.describe().entries)
              ListTile(
                dense: true,
                title: Text(entry.key),
                trailing: Text('${entry.value}'),
              ),
          ],
        ],
      ),
    );
  }

  Future<void> _toggleNotifications(BuildContext context, bool enable) async {
    final provider = context.read<SettingsProvider>();
    if (enable) {
      final granted = await provider.requestNotificationPermission();
      if (!granted) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Notification permission was denied')),
          );
        }
        return;
      }
    }
    await provider.save(provider.settings.copyWith(notificationsEnabled: enable));
  }

  Future<void> _editBaseCurrency(BuildContext context, String current) async {
    final controller = TextEditingController(text: current);
    final code = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Base currency'),
        content: TextField(
          controller: controller,
          textCapitalization: TextCapitalization.characters,
          maxLength: 3,
          decoration: const InputDecoration(labelText: 'ISO 4217 code, e.g. USD'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.of(context).pop(controller.text), child: const Text('Save')),
        ],
      ),
    );
    if (code == null || code.trim().isEmpty || !context.mounted) return;
    final failure = await context.read<SettingsProvider>().setBaseCurrency(code.trim().toUpperCase());
    if (failure != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ErrorHandler.userMessage(failure))));
    }
  }

  Future<void> _refreshRates(BuildContext context) async {
    final failure = await context.read<SettingsProvider>().refreshRates();
    if (failure != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ErrorHandler.userMessage(failure))));
    }
  }

  Future<void> _addManualRate(BuildContext context) async {
    final codeController = TextEditingController();
    final rateController = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Manual exchange rate'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: codeController,
              textCapitalization: TextCapitalization.characters,
              maxLength: 3,
              decoration: const InputDecoration(
                labelText: 'Currency code',
                hintText: 'e.g. EUR, GBP, JPY',
                counterText: '',
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: rateController,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(
                labelText: 'Rate to base',
                hintText: 'e.g. 1.08',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Save')),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;

    final code = codeController.text.trim().toUpperCase();
    final rate = double.tryParse(rateController.text.trim());
    if (code.length != 3 || rate == null || rate <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter a valid 3-letter currency code and positive rate')),
      );
      return;
    }

    final failure = await context.read<SettingsProvider>().setManualRate(
          code: code,
          rateToBase: rate,
        );
    if (failure != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ErrorHandler.userMessage(failure))));
    }
  }

  Future<void> _addCategory(BuildContext context) async {
    final controller = TextEditingController();
    final name = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('New category'),
        content: TextField(controller: controller, autofocus: true),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.of(context).pop(controller.text), child: const Text('Add')),
        ],
      ),
    );
    if (name == null || name.trim().isEmpty || !context.mounted) return;
    final failure = await context.read<SettingsProvider>().addCategory(name.trim());
    if (failure != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ErrorHandler.userMessage(failure))));
    }
  }

  Future<void> _deleteCategory(BuildContext context, String id) async {
    final failure = await context.read<SettingsProvider>().deleteCategory(id);
    if (failure != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ErrorHandler.userMessage(failure))));
    }
  }

  Future<void> _exportCsv(BuildContext context) async {
    final failure = await context.read<SettingsProvider>().exportCsv();
    if (failure != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ErrorHandler.userMessage(failure))));
    }
  }

  Future<void> _eraseAllData(BuildContext context) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Erase all data?'),
        content: const Text('This permanently deletes every expense, budget, category, and template. This cannot be undone.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Cancel')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Theme.of(context).colorScheme.error),
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Erase everything'),
          ),
        ],
      ),
    );
    if (confirmed == true && context.mounted) {
      await context.read<SettingsProvider>().eraseAllData();
    }
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.title);
  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Text(
        title,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: Theme.of(context).colorScheme.primary,
            ),
      ),
    );
  }
}
