import '../../domain/entities/shopping_item.dart';

class ShoppingItemModel extends ShoppingItem {
  const ShoppingItemModel({
    required super.id,
    required super.listId,
    required super.name,
    super.category = 'General',
    super.storeSection = 'Produce & Fresh Herbs',
    super.storeSectionOrder = 0,
    super.quantity = 1.0,
    super.unit = 'pcs',
    super.priceEstimate = 0.0,
    super.isGotIt = false,
    super.isFavorite = false,
    super.isArchived = false,
    required super.addedByUid,
    super.gotItByUid,
    super.hasPendingSync = false,
    required super.updatedAt,
  });

  /// Factory deserializer from Firestore document data
  factory ShoppingItemModel.fromMap(
    Map<String, dynamic> data,
    String id, {
    bool hasPendingWrites = false,
  }) {
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

    return ShoppingItemModel(
      id: id,
      listId: data['listId'] as String? ?? '',
      name: data['name'] as String? ?? '',
      category: data['category'] as String? ?? 'General',
      storeSection: data['storeSection'] as String? ?? 'Produce & Fresh Herbs',
      storeSectionOrder: (data['storeSectionOrder'] as num?)?.toInt() ?? 0,
      quantity: (data['quantity'] as num?)?.toDouble() ?? 1.0,
      unit: data['unit'] as String? ?? 'pcs',
      priceEstimate: (data['priceEstimate'] as num?)?.toDouble() ?? 0.0,
      isGotIt: data['isGotIt'] as bool? ?? false,
      isFavorite: data['isFavorite'] as bool? ?? false,
      isArchived: data['isArchived'] as bool? ?? false,
      addedByUid: data['addedByUid'] as String? ?? '',
      gotItByUid: data['gotItByUid'] as String?,
      hasPendingSync: hasPendingWrites,
      updatedAt: parseDate(data['updatedAt']),
    );
  }

  /// Serializer to Map for Firestore persistence
  Map<String, dynamic> toMap() {
    return {
      'listId': listId,
      'name': name,
      'category': category,
      'storeSection': storeSection,
      'storeSectionOrder': storeSectionOrder,
      'quantity': quantity,
      'unit': unit,
      'priceEstimate': priceEstimate,
      'isGotIt': isGotIt,
      'isFavorite': isFavorite,
      'isArchived': isArchived,
      'addedByUid': addedByUid,
      'gotItByUid': gotItByUid,
      'updatedAt': updatedAt.toIso8601String(),
    };
  }

  /// Maps domain entity to data model
  factory ShoppingItemModel.fromEntity(ShoppingItem entity) {
    return ShoppingItemModel(
      id: entity.id,
      listId: entity.listId,
      name: entity.name,
      category: entity.category,
      storeSection: entity.storeSection,
      storeSectionOrder: entity.storeSectionOrder,
      quantity: entity.quantity,
      unit: entity.unit,
      priceEstimate: entity.priceEstimate,
      isGotIt: entity.isGotIt,
      isFavorite: entity.isFavorite,
      isArchived: entity.isArchived,
      addedByUid: entity.addedByUid,
      gotItByUid: entity.gotItByUid,
      hasPendingSync: entity.hasPendingSync,
      updatedAt: entity.updatedAt,
    );
  }
}
