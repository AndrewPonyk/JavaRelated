/// Value object representing a completed habit milestone on a specific calendar day
class HabitCompletion {
  final String id;
  final String habitId;
  final String completedDate; // "YYYY-MM-DD"
  final int count;
  final DateTime createdAt;

  const HabitCompletion({
    required this.id,
    required this.habitId,
    required this.completedDate,
    this.count = 1,
    required this.createdAt,
  });
}
