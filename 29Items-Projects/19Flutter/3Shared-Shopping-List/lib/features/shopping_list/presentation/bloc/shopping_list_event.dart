import '../../domain/entities/shopping_item.dart';

abstract class ShoppingListEvent {
  const ShoppingListEvent();
}

/// Dispatched to initialize real-time subscription for a given list
class LoadShoppingList extends ShoppingListEvent {
  final String listId;
  const LoadShoppingList(this.listId);
}

/// Dispatched when the real-time snapshot listener yields updated items
class ShoppingItemsUpdated extends ShoppingListEvent {
  final List<ShoppingItem> items;
  const ShoppingItemsUpdated(this.items);
}

/// Dispatched when a shopper toggles the "Got It" checkoff
class ToggleItemGotItEvent extends ShoppingListEvent {
  final String itemId;
  final bool isGotIt;
  final String userId;

  const ToggleItemGotItEvent({
    required this.itemId,
    required this.isGotIt,
    required this.userId,
  });
}

/// Dispatched to add a new grocery item to the current list
class AddShoppingItemEvent extends ShoppingListEvent {
  final String name;
  final String category;
  final String storeSection;
  final double quantity;
  final String unit;
  final double priceEstimate;
  final String userId;

  const AddShoppingItemEvent({
    required this.name,
    this.category = 'General',
    this.storeSection = 'Produce & Fresh Herbs',
    this.quantity = 1.0,
    this.unit = 'pcs',
    this.priceEstimate = 0.0,
    required this.userId,
  });
}

/// Dispatched to filter or re-order by store aisle walking route
class ChangeRouteFilterEvent extends ShoppingListEvent {
  final bool sortByStoreRoute;
  const ChangeRouteFilterEvent({required this.sortByStoreRoute});
}

/// Dispatched to filter items by grocery category
class FilterCategoryEvent extends ShoppingListEvent {
  final String category;
  const FilterCategoryEvent(this.category);
}

/// Dispatched to toggle favorite status of an item
class ToggleItemFavoriteEvent extends ShoppingListEvent {
  final String itemId;
  final bool isFavorite;
  const ToggleItemFavoriteEvent(
      {required this.itemId, required this.isFavorite});
}

/// Dispatched when the user inputs a search keyword
class SearchQueryChangedEvent extends ShoppingListEvent {
  final String query;
  const SearchQueryChangedEvent(this.query);
}

/// Dispatched to update an existing grocery item's details
class UpdateShoppingItemEvent extends ShoppingListEvent {
  final ShoppingItem item;
  const UpdateShoppingItemEvent(this.item);
}

/// Dispatched to remove an item from the list
class DeleteShoppingItemEvent extends ShoppingListEvent {
  final String listId;
  final String itemId;
  const DeleteShoppingItemEvent({required this.listId, required this.itemId});
}
