class Venue {
  const Venue({
    required this.id,
    required this.name,
    required this.venueType,
    required this.timezone,
  });

  factory Venue.fromJson(Map<String, dynamic> json) {
    return Venue(
      id: json['id'] as String,
      name: json['name'] as String,
      venueType: json['venueType'] as String,
      timezone: json['timezone'] as String,
    );
  }

  final String id;
  final String name;
  final String venueType;
  final String timezone;
}
