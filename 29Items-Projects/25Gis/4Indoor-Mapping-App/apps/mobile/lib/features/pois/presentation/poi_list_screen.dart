import 'dart:async';

import 'package:flutter/material.dart';

import '../../../core/config/app_config.dart';
import '../../../core/network/api_client.dart';
import '../data/poi_repository.dart';
import '../domain/floor.dart';
import '../domain/poi.dart';
import '../domain/venue.dart';

class PoiListScreen extends StatefulWidget {
  const PoiListScreen({
    super.key,
    PoiRepository? repository,
  }) : _repository = repository;

  final PoiRepository? _repository;

  @override
  State<PoiListScreen> createState() => _PoiListScreenState();
}

class _PoiListScreenState extends State<PoiListScreen> {
  final _searchController = TextEditingController();
  late final PoiRepository _repository;

  bool _loading = true;
  String? _error;
  List<Venue> _venues = const [];
  List<VenueFloor> _floors = const [];
  List<Poi> _pois = const [];
  Venue? _selectedVenue;
  VenueFloor? _selectedFloor;
  String? _selectedCategory;
  Timer? _debounce;

  @override
  void initState() {
    super.initState();
    final config = AppConfig.fromEnvironment();
    _repository = widget._repository ??
        PoiRepository(ApiClient(baseUrl: config.apiBaseUrl));
    unawaited(_loadInitialData());
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  List<String> get _categories {
    final categories = _pois.map((poi) => poi.category).toSet().toList()
      ..sort();
    return categories;
  }

  Future<void> _loadInitialData() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final venues = await _repository.listVenues();
      final selectedVenue = venues.isEmpty ? null : venues.first;
      final floors = selectedVenue == null
          ? const <VenueFloor>[]
          : await _repository.listFloors(selectedVenue.id);

      setState(() {
        _venues = venues;
        _selectedVenue = selectedVenue;
        _floors = floors;
        _selectedFloor = floors.isEmpty ? null : floors.first;
      });
      await _loadPois();
    } on Object catch (error) {
      setState(() {
        _error = error.toString();
        _loading = false;
      });
    }
  }

  Future<void> _loadFloorsForVenue(Venue venue) async {
    setState(() {
      _selectedVenue = venue;
      _selectedFloor = null;
      _selectedCategory = null;
      _loading = true;
      _error = null;
    });

    try {
      final floors = await _repository.listFloors(venue.id);
      setState(() {
        _floors = floors;
        _selectedFloor = floors.isEmpty ? null : floors.first;
      });
      await _loadPois();
    } on Object catch (error) {
      setState(() {
        _error = error.toString();
        _loading = false;
      });
    }
  }

  Future<void> _loadPois() async {
    final venue = _selectedVenue;
    if (venue == null) {
      setState(() {
        _pois = const [];
        _loading = false;
      });
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final pois = await _repository.searchPois(
        venueId: venue.id,
        floorId: _selectedFloor?.id,
        category: _selectedCategory,
        query: _searchController.text.trim(),
      );
      setState(() {
        _pois = pois;
        _loading = false;
      });
    } on Object catch (error) {
      setState(() {
        _error = error.toString();
        _loading = false;
      });
    }
  }

  void _onSearchChanged(String _) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 350), () {
      unawaited(_loadPois());
    });
  }

  Future<void> _openForm([Poi? poi]) async {
    final venue = _selectedVenue;
    if (venue == null || _floors.isEmpty) {
      return;
    }

    final changed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (context) {
        return PoiFormSheet(
          repository: _repository,
          venue: venue,
          floors: _floors,
          poi: poi,
        );
      },
    );

    if (changed ?? false) {
      await _loadPois();
    }
  }

  Future<void> _deletePoi(Poi poi) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Delete POI'),
          content: Text('Delete ${poi.name}?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('Delete'),
            ),
          ],
        );
      },
    );

    if (confirmed != true) {
      return;
    }

    try {
      await _repository.deletePoi(poi.id);
      await _loadPois();
    } on Object catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.toString())),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final canCreate = _selectedVenue != null && _floors.isNotEmpty;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Indoor Mapping'),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            onPressed: () => unawaited(_loadInitialData()),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      floatingActionButton: canCreate
          ? FloatingActionButton(
              onPressed: () => unawaited(_openForm()),
              child: const Icon(Icons.add_location_alt),
            )
          : null,
      body: RefreshIndicator(
        onRefresh: _loadPois,
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: _Filters(
                venues: _venues,
                floors: _floors,
                categories: _categories,
                selectedVenue: _selectedVenue,
                selectedFloor: _selectedFloor,
                selectedCategory: _selectedCategory,
                searchController: _searchController,
                onVenueChanged: (venue) {
                  if (venue != null) {
                    unawaited(_loadFloorsForVenue(venue));
                  }
                },
                onFloorChanged: (floor) {
                  setState(() => _selectedFloor = floor);
                  unawaited(_loadPois());
                },
                onCategoryChanged: (category) {
                  setState(() => _selectedCategory = category);
                  unawaited(_loadPois());
                },
                onSearchChanged: _onSearchChanged,
              ),
            ),
            if (_loading)
              const SliverFillRemaining(
                hasScrollBody: false,
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_error != null)
              SliverFillRemaining(
                hasScrollBody: false,
                child: _ErrorState(
                  message: _error!,
                  onRetry: _loadInitialData,
                ),
              )
            else if (_selectedVenue == null)
              const SliverFillRemaining(
                hasScrollBody: false,
                child: _EmptyState(
                  icon: Icons.location_city,
                  message: 'No venues are available.',
                ),
              )
            else if (_pois.isEmpty)
              const SliverFillRemaining(
                hasScrollBody: false,
                child: _EmptyState(
                  icon: Icons.search_off,
                  message: 'No POIs match the current filters.',
                ),
              )
            else
              SliverList.separated(
                itemCount: _pois.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (context, index) {
                  final poi = _pois[index];
                  return _PoiTile(
                    poi: poi,
                    floors: _floors,
                    onTap: () => _showPoiDetails(poi),
                    onEdit: () => _openForm(poi),
                    onDelete: () => _deletePoi(poi),
                  );
                },
              ),
          ],
        ),
      ),
    );
  }

  void _showPoiDetails(Poi poi) {
    showModalBottomSheet<void>(
      context: context,
      builder: (context) {
        final floor = _findFloor(_floors, poi.floorId);
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  poi.name,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    Chip(label: Text(poi.category)),
                    if (floor != null) Chip(label: Text(floor.name)),
                  ],
                ),
                if (poi.description != null && poi.description!.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Text(poi.description!),
                ],
                const SizedBox(height: 12),
                Text(
                  '${poi.latitude.toStringAsFixed(6)}, '
                  '${poi.longitude.toStringAsFixed(6)}',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    FilledButton.icon(
                      onPressed: () {
                        Navigator.of(context).pop();
                        unawaited(_openForm(poi));
                      },
                      icon: const Icon(Icons.edit),
                      label: const Text('Edit'),
                    ),
                    const SizedBox(width: 12),
                    OutlinedButton.icon(
                      onPressed: () {
                        Navigator.of(context).pop();
                        unawaited(_deletePoi(poi));
                      },
                      icon: const Icon(Icons.delete_outline),
                      label: const Text('Delete'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _Filters extends StatelessWidget {
  const _Filters({
    required this.venues,
    required this.floors,
    required this.categories,
    required this.selectedVenue,
    required this.selectedFloor,
    required this.selectedCategory,
    required this.searchController,
    required this.onVenueChanged,
    required this.onFloorChanged,
    required this.onCategoryChanged,
    required this.onSearchChanged,
  });

  final List<Venue> venues;
  final List<VenueFloor> floors;
  final List<String> categories;
  final Venue? selectedVenue;
  final VenueFloor? selectedFloor;
  final String? selectedCategory;
  final TextEditingController searchController;
  final ValueChanged<Venue?> onVenueChanged;
  final ValueChanged<VenueFloor?> onFloorChanged;
  final ValueChanged<String?> onCategoryChanged;
  final ValueChanged<String> onSearchChanged;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Column(
        children: [
          LayoutBuilder(
            builder: (context, constraints) {
              final wide = constraints.maxWidth > 720;
              final venueDropdown = _Dropdown<Venue>(
                label: 'Venue',
                value: selectedVenue,
                items: venues,
                itemLabel: (venue) => venue.name,
                onChanged: onVenueChanged,
              );
              final floorDropdown = _Dropdown<VenueFloor>(
                label: 'Floor',
                value: selectedFloor,
                items: floors,
                itemLabel: (floor) => '${floor.name} (${floor.level})',
                onChanged: onFloorChanged,
              );

              if (wide) {
                return Row(
                  children: [
                    Expanded(child: venueDropdown),
                    const SizedBox(width: 12),
                    Expanded(child: floorDropdown),
                  ],
                );
              }

              return Column(
                children: [
                  venueDropdown,
                  const SizedBox(height: 12),
                  floorDropdown,
                ],
              );
            },
          ),
          const SizedBox(height: 12),
          TextField(
            controller: searchController,
            decoration: const InputDecoration(
              prefixIcon: Icon(Icons.search),
              labelText: 'Search POIs',
              border: OutlineInputBorder(),
            ),
            onChanged: onSearchChanged,
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                FilterChip(
                  label: const Text('All categories'),
                  selected: selectedCategory == null,
                  onSelected: (_) => onCategoryChanged(null),
                ),
                for (final category in categories)
                  FilterChip(
                    label: Text(category),
                    selected: selectedCategory == category,
                    onSelected: (_) => onCategoryChanged(category),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Dropdown<T> extends StatelessWidget {
  const _Dropdown({
    required this.label,
    required this.value,
    required this.items,
    required this.itemLabel,
    required this.onChanged,
  });

  final String label;
  final T? value;
  final List<T> items;
  final String Function(T item) itemLabel;
  final ValueChanged<T?> onChanged;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<T>(
      value: value,
      isExpanded: true,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
      ),
      items: [
        for (final item in items)
          DropdownMenuItem<T>(
            value: item,
            child: Text(
              itemLabel(item),
              overflow: TextOverflow.ellipsis,
            ),
          ),
      ],
      onChanged: items.isEmpty ? null : onChanged,
    );
  }
}

class _PoiTile extends StatelessWidget {
  const _PoiTile({
    required this.poi,
    required this.floors,
    required this.onTap,
    required this.onEdit,
    required this.onDelete,
  });

  final Poi poi;
  final List<VenueFloor> floors;
  final VoidCallback onTap;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final floor = _findFloor(floors, poi.floorId);
    return ListTile(
      leading: const CircleAvatar(child: Icon(Icons.place)),
      title: Text(poi.name),
      subtitle: Text(
        [
          poi.category,
          if (floor != null) floor.name,
          if (poi.distanceMeters != null)
            '${poi.distanceMeters!.round()} m away',
        ].join(' - '),
      ),
      onTap: onTap,
      trailing: PopupMenuButton<String>(
        onSelected: (value) {
          if (value == 'edit') {
            onEdit();
          } else if (value == 'delete') {
            onDelete();
          }
        },
        itemBuilder: (context) => const [
          PopupMenuItem(
            value: 'edit',
            child: ListTile(
              leading: Icon(Icons.edit),
              title: Text('Edit'),
            ),
          ),
          PopupMenuItem(
            value: 'delete',
            child: ListTile(
              leading: Icon(Icons.delete_outline),
              title: Text('Delete'),
            ),
          ),
        ],
      ),
    );
  }
}

VenueFloor? _findFloor(List<VenueFloor> floors, String floorId) {
  for (final floor in floors) {
    if (floor.id == floorId) {
      return floor;
    }
  }
  return null;
}

class PoiFormSheet extends StatefulWidget {
  const PoiFormSheet({
    required this.repository,
    required this.venue,
    required this.floors,
    this.poi,
    super.key,
  });

  final PoiRepository repository;
  final Venue venue;
  final List<VenueFloor> floors;
  final Poi? poi;

  @override
  State<PoiFormSheet> createState() => _PoiFormSheetState();
}

class _PoiFormSheetState extends State<PoiFormSheet> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameController;
  late final TextEditingController _categoryController;
  late final TextEditingController _descriptionController;
  late final TextEditingController _latitudeController;
  late final TextEditingController _longitudeController;
  late VenueFloor _selectedFloor;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final poi = widget.poi;
    _selectedFloor = widget.floors.firstWhere(
      (floor) => floor.id == poi?.floorId,
      orElse: () => widget.floors.first,
    );
    _nameController = TextEditingController(text: poi?.name ?? '');
    _categoryController = TextEditingController(text: poi?.category ?? '');
    _descriptionController = TextEditingController(
      text: poi?.description ?? '',
    );
    _latitudeController = TextEditingController(
      text: poi?.latitude.toString() ?? '',
    );
    _longitudeController = TextEditingController(
      text: poi?.longitude.toString() ?? '',
    );
  }

  @override
  void dispose() {
    _nameController.dispose();
    _categoryController.dispose();
    _descriptionController.dispose();
    _latitudeController.dispose();
    _longitudeController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() => _saving = true);
    final input = PoiFormInput(
      venueId: widget.venue.id,
      floorId: _selectedFloor.id,
      name: _nameController.text.trim(),
      category: _categoryController.text.trim(),
      description: _descriptionController.text.trim(),
      latitude: double.parse(_latitudeController.text.trim()),
      longitude: double.parse(_longitudeController.text.trim()),
    );

    try {
      final poi = widget.poi;
      if (poi == null) {
        await widget.repository.createPoi(input);
      } else {
        await widget.repository.updatePoi(poi.id, input);
      }
      if (mounted) {
        Navigator.of(context).pop(true);
      }
    } on Object catch (error) {
      if (!mounted) {
        return;
      }
      setState(() => _saving = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.toString())),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = widget.poi == null ? 'Create POI' : 'Edit POI';
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          top: 16,
          bottom: MediaQuery.viewInsetsOf(context).bottom + 16,
        ),
        child: Form(
          key: _formKey,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 16),
                DropdownButtonFormField<VenueFloor>(
                  value: _selectedFloor,
                  isExpanded: true,
                  decoration: const InputDecoration(
                    labelText: 'Floor',
                    border: OutlineInputBorder(),
                  ),
                  items: [
                    for (final floor in widget.floors)
                      DropdownMenuItem(
                        value: floor,
                        child: Text('${floor.name} (${floor.level})'),
                      ),
                  ],
                  onChanged: _saving
                      ? null
                      : (floor) {
                          if (floor != null) {
                            setState(() => _selectedFloor = floor);
                          }
                        },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _nameController,
                  decoration: const InputDecoration(
                    labelText: 'Name',
                    border: OutlineInputBorder(),
                  ),
                  textInputAction: TextInputAction.next,
                  validator: _required,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _categoryController,
                  decoration: const InputDecoration(
                    labelText: 'Category',
                    border: OutlineInputBorder(),
                  ),
                  textInputAction: TextInputAction.next,
                  validator: _required,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _descriptionController,
                  decoration: const InputDecoration(
                    labelText: 'Description',
                    border: OutlineInputBorder(),
                  ),
                  minLines: 2,
                  maxLines: 3,
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: TextFormField(
                        controller: _latitudeController,
                        decoration: const InputDecoration(
                          labelText: 'Latitude',
                          border: OutlineInputBorder(),
                        ),
                        keyboardType: const TextInputType.numberWithOptions(
                          decimal: true,
                          signed: true,
                        ),
                        validator: (value) => _coordinate(value, -90, 90),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: TextFormField(
                        controller: _longitudeController,
                        decoration: const InputDecoration(
                          labelText: 'Longitude',
                          border: OutlineInputBorder(),
                        ),
                        keyboardType: const TextInputType.numberWithOptions(
                          decimal: true,
                          signed: true,
                        ),
                        validator: (value) => _coordinate(value, -180, 180),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    TextButton(
                      onPressed:
                          _saving ? null : () => Navigator.of(context).pop(),
                      child: const Text('Cancel'),
                    ),
                    const SizedBox(width: 8),
                    FilledButton.icon(
                      onPressed: _saving ? null : _save,
                      icon: _saving
                          ? const SizedBox.square(
                              dimension: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.save),
                      label: const Text('Save'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  String? _required(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Required';
    }
    return null;
  }

  String? _coordinate(String? value, double min, double max) {
    final parsed = double.tryParse(value?.trim() ?? '');
    if (parsed == null) {
      return 'Enter a number';
    }
    if (parsed < min || parsed > max) {
      return 'Range $min to $max';
    }
    return null;
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({
    required this.message,
    required this.onRetry,
  });

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, size: 48),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Retry'),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({
    required this.icon,
    required this.message,
  });

  final IconData icon;
  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 48),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}
