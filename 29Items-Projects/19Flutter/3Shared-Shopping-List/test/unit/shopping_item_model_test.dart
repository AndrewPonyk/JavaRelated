import 'package:flutter_test/flutter_test.dart';
import 'package:shared_shopping_list/features/shopping_list/data/models/shopping_item_model.dart';
import 'package:shared_shopping_list/features/shopping_list/domain/entities/shopping_item.dart';

void main() {
  group('ShoppingItemModel', () {
    final tDateTime = DateTime(2026, 9, 6, 12, 0, 0);

    final tModel = ShoppingItemModel(
      id: 'item-101',
      listId: 'list-001',
      name: 'Organic Honey',
      category: 'Pantry',
      storeSection: 'Pantry & Canned Goods',
      storeSectionOrder: 5,
      quantity: 2.0,
      unit: 'jars',
      priceEstimate: 6.99,
      isGotIt: false,
      isFavorite: true,
      isArchived: false,
      addedByUid: 'user-001',
      hasPendingSync: true,
      updatedAt: tDateTime,
    );

    test('should be a subclass of ShoppingItem entity', () {
      expect(tModel, isA<ShoppingItem>());
    });

    test('fromMap should parse valid JSON / Firestore map with pending writes',
        () {
      final map = {
        'listId': 'list-001',
        'name': 'Organic Honey',
        'category': 'Pantry',
        'storeSection': 'Pantry & Canned Goods',
        'storeSectionOrder': 5,
        'quantity': 2.0,
        'unit': 'jars',
        'priceEstimate': 6.99,
        'isGotIt': false,
        'isFavorite': true,
        'isArchived': false,
        'addedByUid': 'user-001',
        'updatedAt': tDateTime,
      };

      final result =
          ShoppingItemModel.fromMap(map, 'item-101', hasPendingWrites: true);

      expect(result.id, 'item-101');
      expect(result.name, 'Organic Honey');
      expect(result.hasPendingSync, isTrue);
      expect(result.storeSectionOrder, 5);
    });

    test('toMap should output valid serialized map for Firestore write', () {
      final map = tModel.toMap();

      expect(map['listId'], 'list-001');
      expect(map['name'], 'Organic Honey');
      expect(map['quantity'], 2.0);
      expect(map['isGotIt'], isFalse);
    });
  });
}
