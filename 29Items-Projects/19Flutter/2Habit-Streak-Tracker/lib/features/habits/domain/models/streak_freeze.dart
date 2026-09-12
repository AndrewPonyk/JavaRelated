/// Entity representing an applied streak freeze protecting a missed day
class StreakFreeze {
  final String id;
  final String habitId;
  final String freezeDate; // "YYYY-MM-DD"
  final String? reason;
  final DateTime createdAt;

  const StreakFreeze({
    required this.id,
    required this.habitId,
    required this.freezeDate,
    this.reason,
    required this.createdAt,
  });
}
