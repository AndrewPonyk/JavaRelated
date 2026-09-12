import '../entities/shopping_item.dart';
import '../entities/shopping_list.dart';

/// Abstract contract for shopping list data access and real-time streaming
abstract class ShoppingListRepository {
  /// Emits real-time items for a specific list, including local pending write metadata
  Stream<List<ShoppingItem>> watchItems(String listId);

  /// Streams metadata for a specific list
  Stream<ShoppingList?> watchShoppingList(String listId);

  /// Optimistically toggles the "Got It" state of an item
  Future<void> toggleItemGotIt({
    required String listId,
    required String itemId,
    required bool isGotIt,
    required String userId,
  });

  /// Adds a new grocery item to the list
  Future<void> addItem(ShoppingItem item);

  /// Updates an existing item's quantity or details
  Future<void> updateItem(ShoppingItem item);

  /// Removes an item or soft-deletes/archives it
  Future<void> removeItem({required String listId, required String itemId});

  /// Creates a new shopping list
  Future<ShoppingList> createList(
      {required String name, required String ownerUid});
}
