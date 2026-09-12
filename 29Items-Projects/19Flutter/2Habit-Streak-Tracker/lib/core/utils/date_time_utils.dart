/// Utility methods for consistent, timezone-safe date-only handling
class DateTimeUtils {
  /// Format DateTime to ISO-8601 Date String 'YYYY-MM-DD'
  static String toDateString(DateTime dateTime) {
    final year = dateTime.year.toString().padLeft(4, '0');
    final month = dateTime.month.toString().padLeft(2, '0');
    final day = dateTime.day.toString().padLeft(2, '0');
    return '$year-$month-$day';
  }

  /// Parse 'YYYY-MM-DD' into a normalized DateTime at local midnight
  static DateTime parseDateString(String dateString) {
    final parts = dateString.split('-');
    if (parts.length != 3) {
      throw FormatException(
        'Invalid date format, expected YYYY-MM-DD: $dateString',
      );
    }
    final year = int.parse(parts[0]);
    final month = int.parse(parts[1]);
    final day = int.parse(parts[2]);
    return DateTime(year, month, day);
  }

  /// Strip hours, minutes, seconds from DateTime
  static DateTime normalizeDate(DateTime dateTime) {
    return DateTime(dateTime.year, dateTime.month, dateTime.day);
  }

  /// Get today's date normalized to midnight
  static DateTime today() {
    return normalizeDate(DateTime.now());
  }

  /// Get today's date string
  static String todayString() {
    return toDateString(today());
  }

  /// Compare if two dates represent the same calendar day
  static bool isSameDay(DateTime a, DateTime b) {
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }

  /// Parse reminder string "HH:mm" into (hour, minute)
  static (int hour, int minute)? parseTimeString(String? timeStr) {
    if (timeStr == null || !timeStr.contains(':')) return null;
    final parts = timeStr.split(':');
    final hour = int.tryParse(parts[0]);
    final minute = int.tryParse(parts[1]);
    if (hour == null || minute == null) return null;
    return (hour, minute);
  }
}
