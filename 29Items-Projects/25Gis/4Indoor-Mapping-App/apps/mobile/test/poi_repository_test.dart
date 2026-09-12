import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:indoor_mapping_app/core/network/api_client.dart';
import 'package:indoor_mapping_app/features/pois/data/poi_repository.dart';

void main() {
  test('loads venues, floors, and POIs from API envelopes', () async {
    final client = ApiClient(
      baseUrl: 'http://localhost:8080',
      httpClient: MockClient((request) async {
        if (request.url.path == '/api/venues') {
          return http.Response(
            '{"data":[{"id":"venue-1","name":"Central Mall","venueType":"mall","timezone":"Europe/Kiev"}]}',
            200,
          );
        }

        if (request.url.path == '/api/floors') {
          expect(request.url.queryParameters['venueId'], 'venue-1');
          return http.Response(
            '{"data":[{"id":"floor-1","venueId":"venue-1","level":1,"name":"Level 1","mapboxLayerId":"floor-1"}]}',
            200,
          );
        }

        if (request.url.path == '/api/pois') {
          expect(request.url.queryParameters['query'], 'Cafe');
          return http.Response(
            '{"data":[{"id":"poi-1","venueId":"venue-1","floorId":"floor-1","name":"Cafe","category":"food","description":"Coffee","latitude":50.45,"longitude":30.52,"distanceMeters":12.4}]}',
            200,
          );
        }

        return http.Response('{"error":{"message":"missing"}}', 404);
      }),
    );
    final repository = PoiRepository(client);

    final venues = await repository.listVenues();
    final floors = await repository.listFloors('venue-1');
    final pois = await repository.searchPois(
      venueId: 'venue-1',
      floorId: 'floor-1',
      query: 'Cafe',
    );

    expect(venues.single.name, 'Central Mall');
    expect(floors.single.name, 'Level 1');
    expect(pois.single.distanceMeters, 12.4);
  });

  test('throws backend error messages', () async {
    final client = ApiClient(
      baseUrl: 'http://localhost:8080',
      httpClient: MockClient((request) async {
        return http.Response(
          '{"error":{"code":"validation_failed","message":"Request validation failed."}}',
          400,
        );
      }),
    );

    await expectLater(
      client.getList('/api/venues'),
      throwsA(
        isA<ApiClientException>().having(
          (error) => error.message,
          'message',
          'Request validation failed.',
        ),
      ),
    );
  });
}
