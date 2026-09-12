import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../bloc/templates_bloc.dart';
import '../bloc/templates_event.dart';
import '../bloc/templates_state.dart';

class StaplesBottomSheet extends StatefulWidget {
  final String listId;
  final String userId;
  final TemplatesBloc templatesBloc;

  const StaplesBottomSheet({
    super.key,
    required this.listId,
    required this.userId,
    required this.templatesBloc,
  });

  @override
  State<StaplesBottomSheet> createState() => _StaplesBottomSheetState();
}

class _StaplesBottomSheetState extends State<StaplesBottomSheet> {
  @override
  void initState() {
    super.initState();
    widget.templatesBloc.add(const LoadTemplatesEvent());
  }

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<TemplatesState>(
      stream: widget.templatesBloc.stream,
      initialData: widget.templatesBloc.state,
      builder: (context, snapshot) {
        final state = snapshot.data ?? const TemplatesState();

        return Padding(
          padding: const EdgeInsets.fromLTRB(20, 24, 20, 32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Weekly Staples & Templates',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                'Add routine household items to your shopping list with one tap.',
                style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
              ),
              const SizedBox(height: 16),
              if (state.status == TemplatesStatus.loading)
                const Center(child: CircularProgressIndicator())
              else if (state.templates.isEmpty)
                const Text('No templates available.')
              else
                ...state.templates.map((template) {
                  return Card(
                    margin: const EdgeInsets.only(bottom: 12),
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              const Icon(Icons.auto_awesome,
                                  color: AppColors.secondary, size: 20),
                              const SizedBox(width: 8),
                              Text(
                                template.name,
                                style: const TextStyle(
                                    fontWeight: FontWeight.bold, fontSize: 15),
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          Text(
                            template.description,
                            style: TextStyle(
                                fontSize: 12, color: Colors.grey.shade600),
                          ),
                          const SizedBox(height: 8),
                          Wrap(
                            spacing: 6,
                            runSpacing: 4,
                            children: template.items.map((item) {
                              return Chip(
                                label: Text(
                                  '${item.name} (${item.defaultQuantity.toInt()} ${item.defaultUnit})',
                                  style: const TextStyle(fontSize: 11),
                                ),
                                backgroundColor: Colors.grey.shade100,
                              );
                            }).toList(),
                          ),
                          const SizedBox(height: 8),
                          SizedBox(
                            width: double.infinity,
                            child: ElevatedButton.icon(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: AppColors.primary,
                                foregroundColor: Colors.white,
                              ),
                              icon: const Icon(Icons.add, size: 16),
                              label: const Text('Add All Items to Cart'),
                              onPressed: () {
                                widget.templatesBloc.add(
                                  ApplyTemplateEvent(
                                    templateId: template.id,
                                    targetListId: widget.listId,
                                    userId: widget.userId,
                                  ),
                                );
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(
                                    content: Text(
                                        'Added "${template.name}" to shopping list!'),
                                  ),
                                );
                                Navigator.pop(context);
                              },
                            ),
                          ),
                        ],
                      ),
                    ),
                  );
                }),
            ],
          ),
        );
      },
    );
  }
}
