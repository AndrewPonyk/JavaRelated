import '../../../core/network/api_client.dart';
import '../domain/floor.dart';
import '../domain/poi.dart';
import '../domain/venue.dart';

class PoiFormInput {
  const PoiFormInput({
    required this.venueId,
    required this.floorId,
    required this.name,
    required this.category,
    required this.latitude,
    required this.longitude,
    this.description,
  });

  final String venueId;
  final String floorId;
  final String name;
  final String category;
  final double latitude;
  final double longitude;
  final String? description;

  Map<String, dynamic> toCreateJson() {
    return {
      'venueId': venueId,
      'floorId': floorId,
      'name': name,
      'category': category,
      'latitude': latitude,
      'longitude': longitude,
      if (description != null && description!.isNotEmpty)
        'description': description,
    };
  }

  Map<String, dynamic> toUpdateJson() {
    final data = toCreateJson();
    data.remove('venueId');
    return data;
  }
}

class PoiRepository {
  const PoiRepository(this._apiClient);

  final ApiClient _apiClient;

  Future<List<Venue>> listVenues() async {
    final items = await _apiClient.getList('/api/venues');
    return items
        .cast<Map<String, dynamic>>()
        .map(Venue.fromJson)
        .toList(growable: false);
  }

  Future<List<VenueFloor>> listFloors(String venueId) async {
    final path = Uri(
      path: '/api/floors',
      queryParameters: {'venueId': venueId},
    ).toString();
    final items = await _apiClient.getList(path);
    return items
        .cast<Map<String, dynamic>>()
        .map(VenueFloor.fromJson)
        .toList(growable: false);
  }

  Future<List<Poi>> searchPois({
    required String venueId,
    String? query,
    String? floorId,
    String? category,
  }) async {
    final params = <String, String>{
      'venueId': venueId,
      if (query != null && query.isNotEmpty) 'query': query,
      if (floorId != null && floorId.isNotEmpty) 'floorId': floorId,
      if (category != null && category.isNotEmpty) 'category': category,
    };

    final path = Uri(path: '/api/pois', queryParameters: params).toString();
    final items = await _apiClient.getList(path);

    return items
        .cast<Map<String, dynamic>>()
        .map(Poi.fromJson)
        .toList(growable: false);
  }

  Future<Poi> createPoi(PoiFormInput input) async {
    final json = await _apiClient.postObject('/api/pois', input.toCreateJson());
    return Poi.fromJson(json);
  }

  Future<Poi> updatePoi(String id, PoiFormInput input) async {
    final json = await _apiClient.patchObject(
      '/api/pois/$id',
      input.toUpdateJson(),
    );
    return Poi.fromJson(json);
  }

  Future<void> deletePoi(String id) {
    return _apiClient.delete('/api/pois/$id');
  }
}
