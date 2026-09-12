import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/entities/shopping_item.dart';

/// Interactive grocery item tile with optimistic checkoff, quantity pill & sync badge
class ShoppingItemTile extends StatelessWidget {
  final ShoppingItem item;
  final ValueChanged<bool> onToggleGotIt;
  final VoidCallback? onDelete;
  final VoidCallback? onTap;

  const ShoppingItemTile({
    super.key,
    required this.item,
    required this.onToggleGotIt,
    this.onDelete,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Dismissible(
      key: Key(item.id),
      direction: DismissDirection.endToStart,
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 20),
        color: AppColors.error,
        child: const Icon(Icons.delete_outline, color: Colors.white),
      ),
      onDismissed: (_) => onDelete?.call(),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 250),
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        decoration: BoxDecoration(
          color: item.isGotIt ? Colors.grey.shade50 : Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: item.isGotIt ? Colors.grey.shade300 : Colors.grey.shade200,
            width: 1,
          ),
          boxShadow: item.isGotIt
              ? []
              : [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.02),
                    blurRadius: 4,
                    offset: const Offset(0, 2),
                  ),
                ],
        ),
        child: ListTile(
          onTap: onTap,
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
          leading: GestureDetector(
            onTap: () => onToggleGotIt(!item.isGotIt),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              width: 28,
              height: 28,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color:
                    item.isGotIt ? AppColors.gotItSuccess : Colors.transparent,
                border: Border.all(
                  color: item.isGotIt
                      ? AppColors.gotItSuccess
                      : Colors.grey.shade400,
                  width: 2,
                ),
              ),
              child: item.isGotIt
                  ? const Icon(Icons.check, size: 18, color: Colors.white)
                  : null,
            ),
          ),
          title: Row(
            children: [
              Expanded(
                child: Text(
                  item.name,
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: item.isGotIt
                        ? AppColors.textSecondary
                        : AppColors.textPrimary,
                    decoration:
                        item.isGotIt ? TextDecoration.lineThrough : null,
                  ),
                ),
              ),
              if (item.hasPendingSync)
                const Tooltip(
                  message: 'Syncing to cloud...',
                  child: Padding(
                    padding: EdgeInsets.only(left: 6),
                    child: Icon(Icons.sync,
                        size: 14, color: AppColors.syncPending),
                  ),
                ),
            ],
          ),
          subtitle: Row(
            children: [
              Text(
                item.category,
                style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
              ),
              if (item.priceEstimate > 0) ...[
                const Text(' • '),
                Text(
                  '\$${(item.priceEstimate * item.quantity).toStringAsFixed(2)}',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: Colors.grey.shade700,
                  ),
                ),
              ],
            ],
          ),
          trailing: Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: item.isGotIt
                  ? Colors.grey.shade200
                  : AppColors.primaryContainer,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Text(
              '${item.quantity % 1 == 0 ? item.quantity.toInt() : item.quantity} ${item.unit}',
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: item.isGotIt ? Colors.grey.shade600 : AppColors.primary,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
