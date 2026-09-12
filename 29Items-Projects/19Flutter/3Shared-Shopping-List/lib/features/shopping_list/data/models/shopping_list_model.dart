import '../../domain/entities/shopping_list.dart';

class ShoppingListModel extends ShoppingList {
  const ShoppingListModel({
    required super.id,
    required super.name,
    required super.ownerUid,
    required super.memberUids,
    super.memberRoles = const {},
    super.totalEstimatedPrice = 0.0,
    super.totalItemsCount = 0,
    super.gotItItemsCount = 0,
    required super.createdAt,
    required super.updatedAt,
  });

  factory ShoppingListModel.fromMap(Map<String, dynamic> data, String id) {
    DateTime parseDate(dynamic timestamp) {
      if (timestamp == null) return DateTime.now();
      if (timestamp is DateTime) return timestamp;
      try {
        final dynamic t = timestamp;
        final dynamic res = t.toDate();
        if (res is DateTime) return res;
        return DateTime.now();
      } catch (_) {
        return DateTime.now();
      }
    }

    return ShoppingListModel(
      id: id,
      name: data['name'] as String? ?? 'Untitled List',
      ownerUid: data['ownerUid'] as String? ?? '',
      memberUids: List<String>.from(data['memberUids'] as List? ?? []),
      memberRoles: Map<String, String>.from(data['memberRoles'] as Map? ?? {}),
      totalEstimatedPrice:
          (data['totalEstimatedPrice'] as num?)?.toDouble() ?? 0.0,
      totalItemsCount: (data['totalItemsCount'] as num?)?.toInt() ?? 0,
      gotItItemsCount: (data['gotItItemsCount'] as num?)?.toInt() ?? 0,
      createdAt: parseDate(data['createdAt']),
      updatedAt: parseDate(data['updatedAt']),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'name': name,
      'ownerUid': ownerUid,
      'memberUids': memberUids,
      'memberRoles': memberRoles,
      'totalEstimatedPrice': totalEstimatedPrice,
      'totalItemsCount': totalItemsCount,
      'gotItItemsCount': gotItItemsCount,
      'createdAt': createdAt.toIso8601String(),
      'updatedAt': updatedAt.toIso8601String(),
    };
  }
}
