import 'dart:async';
import '../../domain/entities/shopping_item.dart';
import '../../domain/entities/shopping_list.dart';
import '../../domain/repositories/shopping_list_repository.dart';
import '../models/shopping_list_model.dart';

/// Implementation of ShoppingListRepository supporting offline caching & real-time sync
class ShoppingListRepositoryImpl implements ShoppingListRepository {
  // In a full production build, inject FirebaseFirestore instance
  // e.g.: final FirebaseFirestore _firestore;

  // Local in-memory cache for fast local access and stubbing
  final Map<String, List<ShoppingItem>> _itemsCache = {};
  final StreamController<List<ShoppingItem>> _itemsStreamController =
      StreamController<List<ShoppingItem>>.broadcast();

  ShoppingListRepositoryImpl() {
    // Seed initial mock items to demonstrate real-time store aisle grouping
    _itemsCache['list-001'] = [
      ShoppingItem(
        id: 'item-1',
        listId: 'list-001',
        name: 'Organic Avocados',
        category: 'Produce',
        storeSection: 'Produce & Fresh Herbs',
        storeSectionOrder: 0,
        quantity: 3,
        unit: 'pcs',
        priceEstimate: 4.50,
        isGotIt: false,
        addedByUid: 'user-001',
        updatedAt: DateTime.now(),
      ),
      ShoppingItem(
        id: 'item-2',
        listId: 'list-001',
        name: 'Sourdough Boule',
        category: 'Bakery',
        storeSection: 'Bakery & Bread',
        storeSectionOrder: 1,
        quantity: 1,
        unit: 'loaf',
        priceEstimate: 5.99,
        isGotIt: true,
        addedByUid: 'user-002',
        updatedAt: DateTime.now(),
      ),
      ShoppingItem(
        id: 'item-3',
        listId: 'list-001',
        name: 'Oat Milk (Barista Blend)',
        category: 'Dairy',
        storeSection: 'Dairy & Eggs',
        storeSectionOrder: 4,
        quantity: 2,
        unit: 'cartons',
        priceEstimate: 7.50,
        isGotIt: false,
        addedByUid: 'user-001',
        hasPendingSync: true, // Demonstrating pending sync indicator
        updatedAt: DateTime.now(),
      ),
    ];
  }

  @override
  Stream<List<ShoppingItem>> watchItems(String listId) async* {
    // Yield current cache state immediately
    yield _itemsCache[listId] ?? [];

    // In production, listen to Firestore snapshots:
    // yield* _firestore
    //   .collection('shopping_lists/$listId/items')
    //   .where('isArchived', isEqualTo: false)
    //   .snapshots(includeMetadataChanges: true)
    //   .map((snapshot) => snapshot.docs.map((doc) =>
    //       ShoppingItemModel.fromMap(doc.data(), doc.id, hasPendingWrites: doc.metadata.hasPendingWrites)
    //   ).toList());

    yield* _itemsStreamController.stream;
  }

  @override
  Stream<ShoppingList?> watchShoppingList(String listId) {
    return Stream.value(
      ShoppingListModel(
        id: listId,
        name: 'Household Groceries',
        ownerUid: 'user-001',
        memberUids: const ['user-001', 'user-002'],
        totalEstimatedPrice: 17.99,
        totalItemsCount: 3,
        gotItItemsCount: 1,
        createdAt: DateTime.now().subtract(const Duration(days: 3)),
        updatedAt: DateTime.now(),
      ),
    );
  }

  @override
  Future<void> toggleItemGotIt({
    required String listId,
    required String itemId,
    required bool isGotIt,
    required String userId,
  }) async {
    // 1. Optimistically mutate in-memory cache
    final list = _itemsCache[listId] ?? [];
    final index = list.indexWhere((i) => i.id == itemId);
    if (index != -1) {
      final updatedItem = list[index].copyWith(
        isGotIt: isGotIt,
        gotItByUid: isGotIt ? userId : null,
        hasPendingSync: true, // Mark pending sync until server confirms
      );
      list[index] = updatedItem;
      _itemsStreamController.add(List<ShoppingItem>.from(list));

      // 2. In production, write to Firestore document:
      // await _firestore.collection('shopping_lists/$listId/items').doc(itemId).update({
      //   'isGotIt': isGotIt,
      //   'gotItByUid': isGotIt ? userId : null,
      //   'updatedAt': FieldValue.serverTimestamp(),
      // });

      // Simulate remote sync resolution
      Future.delayed(const Duration(milliseconds: 600), () {
        if (index < list.length && list[index].id == itemId) {
          list[index] = list[index].copyWith(hasPendingSync: false);
          _itemsStreamController.add(List<ShoppingItem>.from(list));
        }
      });
    }
  }

  @override
  Future<void> addItem(ShoppingItem item) async {
    final list = _itemsCache[item.listId] ?? [];
    list.add(item);
    _itemsCache[item.listId] = list;
    _itemsStreamController.add(List<ShoppingItem>.from(list));

    // In production:
    // final model = ShoppingItemModel.fromEntity(item);
    // await _firestore.collection('shopping_lists/${item.listId}/items').doc(item.id).set(model.toMap());
  }

  @override
  Future<void> updateItem(ShoppingItem item) async {
    final list = _itemsCache[item.listId] ?? [];
    final index = list.indexWhere((i) => i.id == item.id);
    if (index != -1) {
      list[index] = item;
      _itemsStreamController.add(List<ShoppingItem>.from(list));
    }
  }

  @override
  Future<void> removeItem(
      {required String listId, required String itemId}) async {
    final list = _itemsCache[listId] ?? [];
    list.removeWhere((i) => i.id == itemId);
    _itemsStreamController.add(List<ShoppingItem>.from(list));
  }

  @override
  Future<ShoppingList> createList(
      {required String name, required String ownerUid}) async {
    final newList = ShoppingListModel(
      id: 'list-${DateTime.now().millisecondsSinceEpoch}',
      name: name,
      ownerUid: ownerUid,
      memberUids: [ownerUid],
      createdAt: DateTime.now(),
      updatedAt: DateTime.now(),
    );
    return newList;
  }
}
