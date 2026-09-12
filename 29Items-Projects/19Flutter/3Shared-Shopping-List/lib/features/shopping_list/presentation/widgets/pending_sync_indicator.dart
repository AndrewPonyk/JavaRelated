import 'package:flutter/material.dart';
import '../../../../core/theme/app_colors.dart';

/// Visual banner displayed when local edits are queued awaiting Firestore server commit
class PendingSyncIndicator extends StatelessWidget {
  final int pendingCount;

  const PendingSyncIndicator({
    super.key,
    required this.pendingCount,
  });

  @override
  Widget build(BuildContext context) {
    if (pendingCount <= 0) {
      return const SizedBox.shrink();
    }

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: AppColors.syncPending.withValues(alpha: 0.15),
        border: Border(
          bottom:
              BorderSide(color: AppColors.syncPending.withValues(alpha: 0.4)),
        ),
      ),
      child: Row(
        children: [
          const SizedBox(
            width: 14,
            height: 14,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation<Color>(Colors.orange),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              pendingCount == 1
                  ? '1 change pending sync to household...'
                  : '$pendingCount changes pending sync to household...',
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: Color(0xFFB45309), // Dark amber
              ),
            ),
          ),
          const Icon(Icons.cloud_queue, size: 16, color: Color(0xFFB45309)),
        ],
      ),
    );
  }
}
