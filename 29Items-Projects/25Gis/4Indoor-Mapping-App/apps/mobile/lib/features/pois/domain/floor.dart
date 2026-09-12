class VenueFloor {
  const VenueFloor({
    required this.id,
    required this.venueId,
    required this.level,
    required this.name,
    this.mapboxLayerId,
  });

  factory VenueFloor.fromJson(Map<String, dynamic> json) {
    return VenueFloor(
      id: json['id'] as String,
      venueId: json['venueId'] as String,
      level: json['level'] as int,
      name: json['name'] as String,
      mapboxLayerId: json['mapboxLayerId'] as String?,
    );
  }

  final String id;
  final String venueId;
  final int level;
  final String name;
  final String? mapboxLayerId;
}
