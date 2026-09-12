import 'dart:async';
import '../../domain/entities/shopping_item.dart';
import '../../domain/repositories/shopping_list_repository.dart';
import 'shopping_list_event.dart';
import 'shopping_list_state.dart';

/// State management controller for collaborative shopping list operations
class ShoppingListBloc {
  final ShoppingListRepository _repository;
  StreamSubscription<List<ShoppingItem>>? _itemsSubscription;

  ShoppingListState _state = const ShoppingListState();
  final _stateController = StreamController<ShoppingListState>.broadcast();

  ShoppingListBloc({required ShoppingListRepository repository})
      : _repository = repository;

  ShoppingListState get state => _state;
  Stream<ShoppingListState> get stream => _stateController.stream;

  void emit(ShoppingListState newState) {
    _state = newState;
    if (!_stateController.isClosed) {
      _stateController.add(newState);
    }
  }

  void add(ShoppingListEvent event) {
    if (event is LoadShoppingList) {
      _onLoadShoppingList(event);
    } else if (event is ShoppingItemsUpdated) {
      _onShoppingItemsUpdated(event);
    } else if (event is ToggleItemGotItEvent) {
      _onToggleItemGotIt(event);
    } else if (event is AddShoppingItemEvent) {
      _onAddShoppingItem(event);
    } else if (event is ChangeRouteFilterEvent) {
      _onChangeRouteFilter(event);
    } else if (event is FilterCategoryEvent) {
      emit(state.copyWith(selectedCategory: event.category));
    } else if (event is ToggleItemFavoriteEvent) {
      _onToggleItemFavorite(event);
    } else if (event is SearchQueryChangedEvent) {
      emit(state.copyWith(searchQuery: event.query));
    } else if (event is UpdateShoppingItemEvent) {
      _onUpdateShoppingItem(event);
    } else if (event is DeleteShoppingItemEvent) {
      _onDeleteShoppingItem(event);
    }
  }

  void _onLoadShoppingList(LoadShoppingList event) {
    emit(state.copyWith(
      status: ShoppingListStatus.loading,
      activeListId: event.listId,
    ));

    _itemsSubscription?.cancel();
    _itemsSubscription = _repository.watchItems(event.listId).listen(
      (items) {
        add(ShoppingItemsUpdated(items));
      },
      onError: (Object error) {
        emit(state.copyWith(
          status: ShoppingListStatus.failure,
          errorMessage: error.toString(),
        ));
      },
    );
  }

  void _onShoppingItemsUpdated(ShoppingItemsUpdated event) {
    final pendingCount = event.items.where((i) => i.hasPendingSync).length;
    emit(state.copyWith(
      status: ShoppingListStatus.loaded,
      items: event.items,
      pendingSyncCount: pendingCount,
    ));
  }

  Future<void> _onToggleItemGotIt(ToggleItemGotItEvent event) async {
    // 1. Optimistic Local State Mutation
    final updatedItems = state.items.map((item) {
      if (item.id == event.itemId) {
        return item.copyWith(
          isGotIt: event.isGotIt,
          gotItByUid: event.isGotIt ? event.userId : null,
          hasPendingSync: true, // Mark pending until remote sync resolves
        );
      }
      return item;
    }).toList();

    emit(state.copyWith(
      items: updatedItems,
      pendingSyncCount: updatedItems.where((i) => i.hasPendingSync).length,
    ));

    // 2. Delegate to repository
    try {
      await _repository.toggleItemGotIt(
        listId: state.activeListId,
        itemId: event.itemId,
        isGotIt: event.isGotIt,
        userId: event.userId,
      );
    } catch (e) {
      emit(state.copyWith(
        errorMessage: 'Failed to sync item status: ${e.toString()}',
      ));
    }
  }

  Future<void> _onAddShoppingItem(AddShoppingItemEvent event) async {
    final newItem = ShoppingItem(
      id: 'item-${DateTime.now().millisecondsSinceEpoch}',
      listId: state.activeListId,
      name: event.name,
      category: event.category,
      storeSection: event.storeSection,
      quantity: event.quantity,
      unit: event.unit,
      priceEstimate: event.priceEstimate,
      addedByUid: event.userId,
      hasPendingSync: true,
      updatedAt: DateTime.now(),
    );

    // Optimistic addition
    final updatedList = List<ShoppingItem>.from(state.items)..add(newItem);
    emit(state.copyWith(
      items: updatedList,
      pendingSyncCount: updatedList.where((i) => i.hasPendingSync).length,
    ));

    try {
      await _repository.addItem(newItem);
    } catch (e) {
      emit(state.copyWith(
        errorMessage: 'Failed to add item: ${e.toString()}',
      ));
    }
  }

  void _onChangeRouteFilter(ChangeRouteFilterEvent event) {
    emit(state.copyWith(sortByStoreRoute: event.sortByStoreRoute));
  }

  Future<void> _onToggleItemFavorite(ToggleItemFavoriteEvent event) async {
    final updated = state.items.map((i) {
      if (i.id == event.itemId) {
        return i.copyWith(isFavorite: event.isFavorite);
      }
      return i;
    }).toList();
    emit(state.copyWith(items: updated));
  }

  Future<void> _onUpdateShoppingItem(UpdateShoppingItemEvent event) async {
    await _repository.updateItem(event.item);
  }

  Future<void> _onDeleteShoppingItem(DeleteShoppingItemEvent event) async {
    await _repository.removeItem(listId: event.listId, itemId: event.itemId);
  }

  void close() {
    _itemsSubscription?.cancel();
    _stateController.close();
  }
}
