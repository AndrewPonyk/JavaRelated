/// Modal bottom sheet for one-tap logging of a frequent purchase. Listens to
/// [TemplateRepository] directly (via `Provider<TemplateRepository>.value` in
/// `main.dart`) since no existing provider exposes template listing.
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/error/error_handler.dart';
import '../../data/repositories/expense_repository.dart';
import '../../data/repositories/template_repository.dart';
import '../providers/expense_provider.dart';
import 'expense_list_tile.dart' show categoryIconFor;

Future<void> showQuickAddSheet(BuildContext context) {
  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    builder: (context) => const QuickAddSheet(),
  );
}

class QuickAddSheet extends StatefulWidget {
  const QuickAddSheet({super.key});

  @override
  State<QuickAddSheet> createState() => _QuickAddSheetState();
}

class _QuickAddSheetState extends State<QuickAddSheet> {
  @override
  Widget build(BuildContext context) {
    final repo = context.read<TemplateRepository>();
    final templates = repo.all();

    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Quick add', style: Theme.of(context).textTheme.titleLarge),
                if (templates.isNotEmpty)
                  Text(
                    'Swipe or tap trash to delete',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context).hintColor,
                        ),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            if (templates.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 24),
                child: Text('No templates yet. Save one from an expense to see it here.'),
              )
            else
              ConstrainedBox(
                constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.5),
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: templates.length,
                  itemBuilder: (context, index) {
                    final template = templates[index];
                    return Dismissible(
                      key: ValueKey(template.id),
                      direction: DismissDirection.endToStart,
                      background: Container(
                        alignment: Alignment.centerRight,
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        color: Theme.of(context).colorScheme.errorContainer,
                        child: Icon(Icons.delete_outline,
                            color: Theme.of(context).colorScheme.onErrorContainer),
                      ),
                      onDismissed: (_) async {
                        await repo.delete(template.id);
                        setState(() {});
                      },
                      child: ListTile(
                        leading: CircleAvatar(
                          child: Icon(categoryIconFor(template.iconCodePoint ?? 0xe5d3)),
                        ),
                        title: Text(template.label),
                        subtitle: Text(template.amount.formatted),
                        trailing: IconButton(
                          icon: const Icon(Icons.delete_outline, size: 20),
                          tooltip: 'Delete template',
                          onPressed: () async {
                            await repo.delete(template.id);
                            setState(() {});
                          },
                        ),
                        onTap: () async {
                          final draft = ExpenseDraft(
                            amountInput: template.amount.decimalString,
                            currencyCode: template.currencyCode,
                            categoryId: template.categoryId,
                            date: DateTime.now(),
                          );
                          final failure = await context
                              .read<ExpenseProvider>()
                              .addFromTemplate(draft, template.id);
                          if (context.mounted) Navigator.of(context).pop();
                          if (failure != null && context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(content: Text(ErrorHandler.userMessage(failure))),
                            );
                          }
                        },
                      ),
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }
}
