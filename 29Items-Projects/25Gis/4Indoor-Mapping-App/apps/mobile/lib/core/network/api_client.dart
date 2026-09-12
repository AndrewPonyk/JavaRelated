import 'dart:convert';

import 'package:http/http.dart' as http;

class ApiClient {
  ApiClient({
    required this.baseUrl,
    http.Client? httpClient,
  }) : _httpClient = httpClient ?? http.Client();

  final String baseUrl;
  final http.Client _httpClient;

  Future<List<dynamic>> getList(String path) async {
    final decoded = await _send('GET', path);
    if (decoded['data'] is List) {
      return decoded['data'] as List<dynamic>;
    }

    throw const ApiClientException('Unexpected API response shape');
  }

  Future<Map<String, dynamic>> getObject(String path) async {
    final decoded = await _send('GET', path);
    final data = decoded['data'];
    if (data is Map<String, dynamic>) {
      return data;
    }

    throw const ApiClientException('Unexpected API response shape');
  }

  Future<Map<String, dynamic>> postObject(
    String path,
    Map<String, dynamic> body,
  ) async {
    final decoded = await _send('POST', path, body: body);
    final data = decoded['data'];
    if (data is Map<String, dynamic>) {
      return data;
    }

    throw const ApiClientException('Unexpected API response shape');
  }

  Future<Map<String, dynamic>> patchObject(
    String path,
    Map<String, dynamic> body,
  ) async {
    final decoded = await _send('PATCH', path, body: body);
    final data = decoded['data'];
    if (data is Map<String, dynamic>) {
      return data;
    }

    throw const ApiClientException('Unexpected API response shape');
  }

  Future<void> delete(String path) async {
    await _send('DELETE', path, allowEmpty: true);
  }

  Future<Map<String, dynamic>> _send(
    String method,
    String path, {
    Map<String, dynamic>? body,
    bool allowEmpty = false,
  }) async {
    final uri = Uri.parse('$baseUrl$path');
    final headers = <String, String>{
      'accept': 'application/json',
      if (body != null) 'content-type': 'application/json',
    };

    final response = switch (method) {
      'GET' => await _httpClient.get(uri, headers: headers),
      'POST' => await _httpClient.post(
          uri,
          headers: headers,
          body: jsonEncode(body),
        ),
      'PATCH' => await _httpClient.patch(
          uri,
          headers: headers,
          body: jsonEncode(body),
        ),
      'DELETE' => await _httpClient.delete(uri, headers: headers),
      _ => throw ApiClientException('Unsupported method $method'),
    };

    if (allowEmpty && response.statusCode == 204) {
      return const <String, dynamic>{};
    }

    final decoded = response.body.isEmpty ? null : jsonDecode(response.body);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      if (decoded is Map<String, dynamic>) {
        final error = decoded['error'];
        if (error is Map<String, dynamic> && error['message'] is String) {
          throw ApiClientException(error['message'] as String);
        }
      }

      throw ApiClientException(
        'Request failed with status ${response.statusCode}',
      );
    }

    if (decoded is Map<String, dynamic>) {
      return decoded;
    }

    throw const ApiClientException('Unexpected API response shape');
  }
}

class ApiClientException implements Exception {
  const ApiClientException(this.message);

  final String message;

  @override
  String toString() => message;
}
