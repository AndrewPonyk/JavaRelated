import 'package:flutter/material.dart';
import '../../../../core/constants/item_categories.dart';
import '../../../../core/constants/store_sections.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../auth/presentation/bloc/auth_bloc.dart';
import '../../../auth/presentation/widgets/user_profile_modal.dart';
import '../../../collaboration/presentation/bloc/invitation_bloc.dart';
import '../../../collaboration/presentation/widgets/share_list_modal.dart';
import '../../../templates/presentation/bloc/templates_bloc.dart';
import '../../../templates/presentation/widgets/staples_bottom_sheet.dart';
import '../../domain/entities/shopping_item.dart';
import '../../domain/repositories/shopping_list_repository.dart';
import '../bloc/shopping_list_bloc.dart';
import '../bloc/shopping_list_event.dart';
import '../bloc/shopping_list_state.dart';
import '../widgets/pending_sync_indicator.dart';
import '../widgets/shopping_item_tile.dart';

/// Collaborative shopping list screen with real-time stream sync, route sorting, and full integrations
class ShoppingListScreen extends StatefulWidget {
  final String listId;
  final ShoppingListRepository repository;
  final AuthBloc authBloc;
  final InvitationBloc invitationBloc;
  final TemplatesBloc templatesBloc;

  const ShoppingListScreen({
    super.key,
    required this.listId,
    required this.repository,
    required this.authBloc,
    required this.invitationBloc,
    required this.templatesBloc,
  });

  @override
  State<ShoppingListScreen> createState() => _ShoppingListScreenState();
}

class _ShoppingListScreenState extends State<ShoppingListScreen> {
  late final ShoppingListBloc _bloc;
  bool _isSearchOpen = false;
  final _searchController = TextEditingController();

  String get _currentUserId => widget.authBloc.state.user?.uid ?? 'user-001';

  @override
  void initState() {
    super.initState();
    _bloc = ShoppingListBloc(repository: widget.repository);
    _bloc.add(LoadShoppingList(widget.listId));
  }

  @override
  void dispose() {
    _searchController.dispose();
    _bloc.close();
    super.dispose();
  }

  void _showAddItemModal(BuildContext context) {
    final nameController = TextEditingController();
    final qtyController = TextEditingController(text: '1');
    final priceController = TextEditingController();
    String selectedSection = StoreSections.entranceProduce;
    String selectedCategory = ItemCategories.produce;

    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return Padding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.of(ctx).viewInsets.bottom + 20,
            top: 20,
            left: 20,
            right: 20,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Add Grocery Item',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: nameController,
                autofocus: true,
                decoration: const InputDecoration(
                  labelText: 'Item Name',
                  hintText: 'e.g. Honeycrisp Apples',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    flex: 2,
                    child: TextField(
                      controller: qtyController,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                        labelText: 'Quantity',
                        border: OutlineInputBorder(),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    flex: 3,
                    child: TextField(
                      controller: priceController,
                      keyboardType:
                          const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(
                        labelText: 'Price Est. (\$)',
                        prefixText: '\$',
                        border: OutlineInputBorder(),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      initialValue: selectedCategory,
                      decoration: const InputDecoration(
                        labelText: 'Category',
                        border: OutlineInputBorder(),
                      ),
                      items: ItemCategories.categories.map((cat) {
                        return DropdownMenuItem(
                            value: cat.name, child: Text(cat.name));
                      }).toList(),
                      onChanged: (val) {
                        if (val != null) selectedCategory = val;
                      },
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      initialValue: selectedSection,
                      decoration: const InputDecoration(
                        labelText: 'Store Aisle',
                        border: OutlineInputBorder(),
                      ),
                      items: StoreSections.walkingOrder.map((section) {
                        return DropdownMenuItem(
                            value: section, child: Text(section));
                      }).toList(),
                      onChanged: (val) {
                        if (val != null) selectedSection = val;
                      },
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                  onPressed: () {
                    if (nameController.text.trim().isNotEmpty) {
                      _bloc.add(
                        AddShoppingItemEvent(
                          name: nameController.text.trim(),
                          category: selectedCategory,
                          quantity: double.tryParse(qtyController.text) ?? 1.0,
                          priceEstimate:
                              double.tryParse(priceController.text) ?? 0.0,
                          storeSection: selectedSection,
                          userId: _currentUserId,
                        ),
                      );
                      Navigator.pop(ctx);
                    }
                  },
                  child: const Text('Add to Shopping List'),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  void _showEditItemModal(BuildContext context, ShoppingItem item) {
    final nameController = TextEditingController(text: item.name);
    final qtyController = TextEditingController(
      text: item.quantity % 1 == 0
          ? item.quantity.toInt().toString()
          : item.quantity.toString(),
    );
    final priceController = TextEditingController(
      text: item.priceEstimate > 0 ? item.priceEstimate.toStringAsFixed(2) : '',
    );
    String selectedSection = item.storeSection;
    String selectedCategory = item.category;

    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) {
        return Padding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.of(ctx).viewInsets.bottom + 20,
            top: 20,
            left: 20,
            right: 20,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Edit Grocery Item',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  IconButton(
                    icon: const Icon(Icons.delete_outline,
                        color: AppColors.error),
                    tooltip: 'Delete Item',
                    onPressed: () {
                      _bloc.add(
                        DeleteShoppingItemEvent(
                          listId: item.listId,
                          itemId: item.id,
                        ),
                      );
                      Navigator.pop(ctx);
                    },
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextField(
                controller: nameController,
                decoration: const InputDecoration(
                  labelText: 'Item Name',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    flex: 2,
                    child: TextField(
                      controller: qtyController,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                        labelText: 'Quantity',
                        border: OutlineInputBorder(),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    flex: 3,
                    child: TextField(
                      controller: priceController,
                      keyboardType:
                          const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(
                        labelText: 'Price Est. (\$)',
                        prefixText: '\$',
                        border: OutlineInputBorder(),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      initialValue: selectedCategory,
                      decoration: const InputDecoration(
                        labelText: 'Category',
                        border: OutlineInputBorder(),
                      ),
                      items: ItemCategories.categories.map((cat) {
                        return DropdownMenuItem(
                            value: cat.name, child: Text(cat.name));
                      }).toList(),
                      onChanged: (val) {
                        if (val != null) selectedCategory = val;
                      },
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: DropdownButtonFormField<String>(
                      initialValue: selectedSection,
                      decoration: const InputDecoration(
                        labelText: 'Store Aisle',
                        border: OutlineInputBorder(),
                      ),
                      items: StoreSections.walkingOrder.map((section) {
                        return DropdownMenuItem(
                            value: section, child: Text(section));
                      }).toList(),
                      onChanged: (val) {
                        if (val != null) selectedSection = val;
                      },
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                  onPressed: () {
                    if (nameController.text.trim().isNotEmpty) {
                      _bloc.add(
                        UpdateShoppingItemEvent(
                          item.copyWith(
                            name: nameController.text.trim(),
                            category: selectedCategory,
                            quantity: double.tryParse(qtyController.text) ??
                                item.quantity,
                            priceEstimate:
                                double.tryParse(priceController.text) ??
                                    item.priceEstimate,
                            storeSection: selectedSection,
                          ),
                        ),
                      );
                      Navigator.pop(ctx);
                    }
                  },
                  child: const Text('Save Changes'),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  void _openShareModal() {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => ShareListModal(
        listId: widget.listId,
        invitationBloc: widget.invitationBloc,
      ),
    );
  }

  void _openStaplesModal() {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => StaplesBottomSheet(
        listId: widget.listId,
        userId: _currentUserId,
        templatesBloc: widget.templatesBloc,
      ),
    );
  }

  void _openProfileModal() {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => UserProfileModal(authBloc: widget.authBloc),
    );
  }

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<ShoppingListState>(
      stream: _bloc.stream,
      initialData: _bloc.state,
      builder: (context, snapshot) {
        final state = snapshot.data ?? const ShoppingListState();

        return Scaffold(
          appBar: AppBar(
            title: _isSearchOpen
                ? TextField(
                    controller: _searchController,
                    autofocus: true,
                    decoration: const InputDecoration(
                      hintText: 'Search grocery items...',
                      border: InputBorder.none,
                    ),
                    onChanged: (query) =>
                        _bloc.add(SearchQueryChangedEvent(query)),
                  )
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Family Grocery Run'),
                      Text(
                        '${state.gotItCount} of ${state.items.length} items checked off',
                        style: const TextStyle(
                            fontSize: 12, color: AppColors.textSecondary),
                      ),
                    ],
                  ),
            actions: [
              IconButton(
                icon: Icon(_isSearchOpen ? Icons.close : Icons.search),
                tooltip: 'Search Items',
                onPressed: () {
                  setState(() {
                    _isSearchOpen = !_isSearchOpen;
                    if (!_isSearchOpen) {
                      _searchController.clear();
                      _bloc.add(const SearchQueryChangedEvent(''));
                    }
                  });
                },
              ),
              IconButton(
                icon: const Icon(Icons.auto_awesome),
                tooltip: 'Weekly Staples',
                onPressed: _openStaplesModal,
              ),
              IconButton(
                icon: const Icon(Icons.share_outlined),
                tooltip: 'Share Invite Link',
                onPressed: _openShareModal,
              ),
              IconButton(
                icon: const Icon(Icons.account_circle_outlined),
                tooltip: 'Account & Linking',
                onPressed: _openProfileModal,
              ),
            ],
            bottom: PreferredSize(
              preferredSize: const Size.fromHeight(4),
              child: LinearProgressIndicator(
                value: state.items.isEmpty
                    ? 0
                    : (state.gotItCount / state.items.length),
                backgroundColor: Colors.grey.shade200,
                valueColor:
                    const AlwaysStoppedAnimation<Color>(AppColors.gotItSuccess),
              ),
            ),
          ),
          body: Column(
            children: [
              // Pending Sync Banner
              PendingSyncIndicator(pendingCount: state.pendingSyncCount),

              // Category Filter Chips
              SizedBox(
                height: 48,
                child: ListView(
                  scrollDirection: Axis.horizontal,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  children: [
                    _buildCategoryChip(
                        'All Items', state.selectedCategory == 'All Items'),
                    ...ItemCategories.categories.map(
                      (cat) => _buildCategoryChip(
                          cat.name, state.selectedCategory == cat.name),
                    ),
                  ],
                ),
              ),

              // Running Cost Summary Card
              if (state.totalEstimatedCost > 0)
                Container(
                  margin: const EdgeInsets.fromLTRB(16, 4, 16, 4),
                  padding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  decoration: BoxDecoration(
                    color: AppColors.primaryContainer.withValues(alpha: 0.5),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'Estimated Cart Total:',
                        style: TextStyle(fontWeight: FontWeight.w600),
                      ),
                      Text(
                        '\$${state.totalEstimatedCost.toStringAsFixed(2)}',
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          color: AppColors.primary,
                          fontSize: 16,
                        ),
                      ),
                    ],
                  ),
                ),

              // Main List View with State Switch
              Expanded(
                child: _buildBody(state),
              ),
            ],
          ),
          floatingActionButton: FloatingActionButton.extended(
            onPressed: () => _showAddItemModal(context),
            icon: const Icon(Icons.add_shopping_cart),
            label: const Text('Add Item'),
          ),
        );
      },
    );
  }

  Widget _buildCategoryChip(String title, bool isSelected) {
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: FilterChip(
        selected: isSelected,
        label: Text(title,
            style: TextStyle(
                fontSize: 12,
                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal)),
        selectedColor: AppColors.primaryContainer,
        checkmarkColor: AppColors.primary,
        onSelected: (_) => _bloc.add(FilterCategoryEvent(title)),
      ),
    );
  }

  Widget _buildBody(ShoppingListState state) {
    if (state.status == ShoppingListStatus.loading) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 12),
            Text('Syncing list with household...'),
          ],
        ),
      );
    }

    if (state.status == ShoppingListStatus.failure) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 48, color: AppColors.error),
            const SizedBox(height: 12),
            Text(state.errorMessage ?? 'Unable to connect to shared list.'),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: () => _bloc.add(LoadShoppingList(widget.listId)),
              child: const Text('Retry Connection'),
            ),
          ],
        ),
      );
    }

    final displayItems = state.filteredItems;
    if (displayItems.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.shopping_bag_outlined,
                size: 64, color: Colors.grey.shade400),
            const SizedBox(height: 12),
            Text(
              state.searchQuery.isNotEmpty
                  ? 'No items matching "${state.searchQuery}"'
                  : 'Your shopping list is empty!',
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 6),
            const Text(
              'Tap "+ Add Item" below or choose from Weekly Staples.',
              style: TextStyle(color: AppColors.textSecondary),
            ),
          ],
        ),
      );
    }

    final grouped = state.groupedByStoreSection;

    return ListView.builder(
      padding: const EdgeInsets.only(top: 8, bottom: 80),
      itemCount: grouped.keys.length,
      itemBuilder: (context, index) {
        final sectionName = grouped.keys.elementAt(index);
        final itemsInSection = grouped[sectionName] ?? [];

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 12, 18, 6),
              child: Row(
                children: [
                  const Icon(Icons.store_mall_directory_outlined,
                      size: 16, color: AppColors.primary),
                  const SizedBox(width: 6),
                  Text(
                    sectionName.toUpperCase(),
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 0.8,
                      color: AppColors.primary,
                    ),
                  ),
                ],
              ),
            ),
            ...itemsInSection.map((item) {
              return ShoppingItemTile(
                item: item,
                onTap: () => _showEditItemModal(context, item),
                onToggleGotIt: (newValue) {
                  _bloc.add(
                    ToggleItemGotItEvent(
                      itemId: item.id,
                      isGotIt: newValue,
                      userId: _currentUserId,
                    ),
                  );
                },
                onDelete: () {
                  _bloc.add(
                    DeleteShoppingItemEvent(
                      listId: item.listId,
                      itemId: item.id,
                    ),
                  );
                },
              );
            }),
          ],
        );
      },
    );
  }
}
