import '../../domain/entities/shopping_item.dart';

enum ShoppingListStatus { initial, loading, loaded, failure }

class ShoppingListState {
  final ShoppingListStatus status;
  final String activeListId;
  final List<ShoppingItem> items;
  final int pendingSyncCount;
  final bool sortByStoreRoute;
  final String selectedCategory;
  final String searchQuery;
  final String? errorMessage;

  const ShoppingListState({
    this.status = ShoppingListStatus.initial,
    this.activeListId = '',
    this.items = const [],
    this.pendingSyncCount = 0,
    this.sortByStoreRoute = true,
    this.selectedCategory = 'All Items',
    this.searchQuery = '',
    this.errorMessage,
  });

  /// Filtered items based on category and search query
  List<ShoppingItem> get filteredItems {
    return items.where((item) {
      final matchesCategory = selectedCategory == 'All Items' ||
          item.category.toLowerCase() == selectedCategory.toLowerCase();
      final matchesSearch = searchQuery.isEmpty ||
          item.name.toLowerCase().contains(searchQuery.toLowerCase());
      return matchesCategory && matchesSearch;
    }).toList();
  }

  /// Computed property: count of items already marked "Got It"
  int get gotItCount => items.where((i) => i.isGotIt).length;

  /// Computed property: total estimated cost of all items
  double get totalEstimatedCost => items.fold(
      0.0, (acc, item) => acc + (item.priceEstimate * item.quantity));

  /// Group filtered items by store section for walking route rendering
  Map<String, List<ShoppingItem>> get groupedByStoreSection {
    final Map<String, List<ShoppingItem>> groups = {};
    final list = filteredItems;
    for (final item in list) {
      groups.putIfAbsent(item.storeSection, () => []).add(item);
    }
    return groups;
  }

  ShoppingListState copyWith({
    ShoppingListStatus? status,
    String? activeListId,
    List<ShoppingItem>? items,
    int? pendingSyncCount,
    bool? sortByStoreRoute,
    String? selectedCategory,
    String? searchQuery,
    String? errorMessage,
  }) {
    return ShoppingListState(
      status: status ?? this.status,
      activeListId: activeListId ?? this.activeListId,
      items: items ?? this.items,
      pendingSyncCount: pendingSyncCount ?? this.pendingSyncCount,
      sortByStoreRoute: sortByStoreRoute ?? this.sortByStoreRoute,
      selectedCategory: selectedCategory ?? this.selectedCategory,
      searchQuery: searchQuery ?? this.searchQuery,
      errorMessage: errorMessage ?? this.errorMessage,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ShoppingListState &&
          runtimeType == other.runtimeType &&
          status == other.status &&
          activeListId == other.activeListId &&
          items.length == other.items.length &&
          pendingSyncCount == other.pendingSyncCount &&
          sortByStoreRoute == other.sortByStoreRoute &&
          selectedCategory == other.selectedCategory &&
          searchQuery == other.searchQuery &&
          errorMessage == other.errorMessage;

  @override
  int get hashCode =>
      status.hashCode ^
      activeListId.hashCode ^
      items.length.hashCode ^
      pendingSyncCount.hashCode ^
      sortByStoreRoute.hashCode ^
      selectedCategory.hashCode ^
      searchQuery.hashCode ^
      errorMessage.hashCode;
}
