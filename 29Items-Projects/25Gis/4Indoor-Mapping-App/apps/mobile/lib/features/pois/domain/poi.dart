class Poi {
  const Poi({
    required this.id,
    required this.venueId,
    required this.name,
    required this.category,
    required this.floorId,
    required this.latitude,
    required this.longitude,
    this.description,
    this.distanceMeters,
  });

  factory Poi.fromJson(Map<String, dynamic> json) {
    return Poi(
      id: json['id'] as String,
      venueId: json['venueId'] as String,
      name: json['name'] as String,
      category: json['category'] as String,
      floorId: json['floorId'] as String,
      latitude: (json['latitude'] as num).toDouble(),
      longitude: (json['longitude'] as num).toDouble(),
      description: json['description'] as String?,
      distanceMeters: (json['distanceMeters'] as num?)?.toDouble(),
    );
  }

  final String id;
  final String venueId;
  final String name;
  final String category;
  final String floorId;
  final double latitude;
  final double longitude;
  final String? description;
  final double? distanceMeters;
}
