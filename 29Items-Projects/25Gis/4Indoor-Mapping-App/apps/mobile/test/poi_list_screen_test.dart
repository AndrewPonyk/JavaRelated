import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:indoor_mapping_app/core/network/api_client.dart';
import 'package:indoor_mapping_app/features/pois/data/poi_repository.dart';
import 'package:indoor_mapping_app/features/pois/presentation/poi_list_screen.dart';

void main() {
  testWidgets('renders loaded venue filters and POI rows', (tester) async {
    final repository = PoiRepository(
      ApiClient(
        baseUrl: 'http://localhost:8080',
        httpClient: MockClient((request) async {
          if (request.url.path == '/api/venues') {
            return http.Response(
              '{"data":[{"id":"venue-1","name":"Central Mall","venueType":"mall","timezone":"Europe/Kiev"}]}',
              200,
            );
          }

          if (request.url.path == '/api/floors') {
            return http.Response(
              '{"data":[{"id":"floor-1","venueId":"venue-1","level":1,"name":"Level 1","mapboxLayerId":"floor-1"}]}',
              200,
            );
          }

          if (request.url.path == '/api/pois') {
            return http.Response(
              '{"data":[{"id":"poi-1","venueId":"venue-1","floorId":"floor-1","name":"Cafe","category":"food","description":"Coffee","latitude":50.45,"longitude":30.52}]}',
              200,
            );
          }

          return http.Response('{"error":{"message":"missing"}}', 404);
        }),
      ),
    );

    await tester.pumpWidget(
      MaterialApp(home: PoiListScreen(repository: repository)),
    );
    await tester.pumpAndSettle();

    expect(find.text('Indoor Mapping'), findsOneWidget);
    expect(find.text('Central Mall'), findsOneWidget);
    expect(find.text('Cafe'), findsOneWidget);
    expect(find.text('food'), findsWidgets);
  });

  testWidgets('renders empty venue state', (tester) async {
    final repository = PoiRepository(
      ApiClient(
        baseUrl: 'http://localhost:8080',
        httpClient: MockClient((request) async {
          return http.Response('{"data":[]}', 200);
        }),
      ),
    );

    await tester.pumpWidget(
      MaterialApp(home: PoiListScreen(repository: repository)),
    );
    await tester.pumpAndSettle();

    expect(find.text('No venues are available.'), findsOneWidget);
  });
}
