import 'package:flutter_test/flutter_test.dart';
import 'package:shared_shopping_list/features/shopping_list/domain/entities/shopping_item.dart';
import 'package:shared_shopping_list/features/shopping_list/domain/entities/shopping_list.dart';
import 'package:shared_shopping_list/features/shopping_list/domain/repositories/shopping_list_repository.dart';
import 'package:shared_shopping_list/features/shopping_list/presentation/bloc/shopping_list_bloc.dart';
import 'package:shared_shopping_list/features/shopping_list/presentation/bloc/shopping_list_event.dart';
import 'package:shared_shopping_list/features/shopping_list/presentation/bloc/shopping_list_state.dart';

class FakeShoppingListRepository implements ShoppingListRepository {
  List<ShoppingItem> items = [];

  @override
  Stream<List<ShoppingItem>> watchItems(String listId) {
    return Stream.value(items);
  }

  @override
  Stream<ShoppingList?> watchShoppingList(String listId) {
    return Stream.value(null);
  }

  @override
  Future<void> toggleItemGotIt({
    required String listId,
    required String itemId,
    required bool isGotIt,
    required String userId,
  }) async {
    final idx = items.indexWhere((i) => i.id == itemId);
    if (idx != -1) {
      items[idx] = items[idx].copyWith(isGotIt: isGotIt);
    }
  }

  @override
  Future<void> addItem(ShoppingItem item) async {
    items.add(item);
  }

  @override
  Future<void> updateItem(ShoppingItem item) async {}

  @override
  Future<void> removeItem(
      {required String listId, required String itemId}) async {
    items.removeWhere((i) => i.id == itemId);
  }

  @override
  Future<ShoppingList> createList(
      {required String name, required String ownerUid}) async {
    throw UnimplementedError();
  }
}

void main() {
  group('ShoppingListBloc', () {
    late FakeShoppingListRepository fakeRepo;
    late ShoppingListBloc bloc;

    final tItem = ShoppingItem(
      id: 'item-001',
      listId: 'list-001',
      name: 'Oat Milk',
      category: 'Dairy',
      storeSection: 'Dairy & Eggs',
      isGotIt: false,
      addedByUid: 'user-001',
      updatedAt: DateTime.now(),
    );

    setUp(() {
      fakeRepo = FakeShoppingListRepository();
      fakeRepo.items = [tItem];
      bloc = ShoppingListBloc(repository: fakeRepo);
    });

    tearDown(() {
      bloc.close();
    });

    test('initial state has initial status and empty items', () {
      expect(bloc.state.status, ShoppingListStatus.initial);
      expect(bloc.state.items, isEmpty);
    });

    test('LoadShoppingList emits loading and then loaded with items', () async {
      final states = <ShoppingListState>[];
      final subscription = bloc.stream.listen(states.add);

      bloc.add(const LoadShoppingList('list-001'));

      // Allow event loop to process
      await Future<void>.delayed(const Duration(milliseconds: 50));

      expect(states.length, greaterThanOrEqualTo(1));
      expect(bloc.state.status, ShoppingListStatus.loaded);
      expect(bloc.state.items.length, 1);
      expect(bloc.state.items.first.name, 'Oat Milk');

      await subscription.cancel();
    });

    test('ToggleItemGotItEvent optimistically updates isGotIt state', () async {
      bloc.add(const LoadShoppingList('list-001'));
      await Future<void>.delayed(const Duration(milliseconds: 50));

      bloc.add(const ToggleItemGotItEvent(
        itemId: 'item-001',
        isGotIt: true,
        userId: 'user-001',
      ));

      expect(bloc.state.items.first.isGotIt, isTrue);
      expect(bloc.state.gotItCount, 1);
    });
  });
}
