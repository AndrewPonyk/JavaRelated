package com.example.inventory.inventory.service;

import com.example.inventory.audit.AuditService;
import com.example.inventory.common.error.BusinessRuleException;
import com.example.inventory.common.error.ConflictException;
import com.example.inventory.common.error.NotFoundException;
import com.example.inventory.common.web.RequestContext;
import com.example.inventory.idempotency.IdempotencyService;
import com.example.inventory.inventory.domain.InventoryItem;
import com.example.inventory.inventory.domain.Warehouse;
import com.example.inventory.inventory.dto.InventoryCreateRequest;
import com.example.inventory.inventory.dto.InventoryResponse;
import com.example.inventory.inventory.dto.InventoryUpdateRequest;
import com.example.inventory.inventory.dto.ItemDeactivateRequest;
import com.example.inventory.inventory.repository.InventoryItemRepository;
import com.example.inventory.inventory.repository.WarehouseRepository;
import com.example.inventory.messaging.StockChangedEvent;
import com.example.inventory.outbox.OutboxService;
import com.example.inventory.stock.domain.ItemBarcode;
import com.example.inventory.stock.domain.MovementType;
import com.example.inventory.stock.domain.StockMovement;
import com.example.inventory.stock.dto.BarcodeRequest;
import com.example.inventory.stock.dto.BarcodeResponse;
import com.example.inventory.stock.repository.ItemBarcodeRepository;
import com.example.inventory.stock.repository.ReservationRepository;
import com.example.inventory.stock.repository.StockMovementRepository;
import com.example.inventory.stock.service.BarcodeValidator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final ItemBarcodeRepository barcodeRepository;
    private final StockMovementRepository movementRepository;
    private final ReservationRepository reservationRepository;
    private final BarcodeValidator barcodeValidator;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final RequestContext requestContext;

    public InventoryService(InventoryItemRepository itemRepository,
                            WarehouseRepository warehouseRepository,
                            ItemBarcodeRepository barcodeRepository,
                            StockMovementRepository movementRepository,
                            ReservationRepository reservationRepository,
                            BarcodeValidator barcodeValidator,
                            IdempotencyService idempotencyService,
                            OutboxService outboxService,
                            AuditService auditService,
                            RequestContext requestContext) {
        this.itemRepository = itemRepository;
        this.warehouseRepository = warehouseRepository;
        this.barcodeRepository = barcodeRepository;
        this.movementRepository = movementRepository;
        this.reservationRepository = reservationRepository;
        this.barcodeValidator = barcodeValidator;
        this.idempotencyService = idempotencyService;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.requestContext = requestContext;
    }

    @Transactional(readOnly = true)
    public Page<InventoryResponse> list(UUID warehouseId, String query, Boolean active, Pageable pageable) {
        Specification<InventoryItem> specification = (root, ignored, builder) -> builder.conjunction();
        if (warehouseId != null) {
            specification = specification.and((root, ignored, builder) ->
                    builder.equal(root.get("warehouse").get("id"), warehouseId));
        }
        if (active != null) {
            specification = specification.and((root, ignored, builder) ->
                    builder.equal(root.get("active"), active));
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, ignored, builder) -> builder.or(
                    builder.like(builder.lower(root.get("sku")), pattern),
                    builder.like(builder.lower(root.get("name")), pattern),
                    builder.like(builder.lower(root.get("barcode")), pattern)));
        }
        Page<InventoryItem> items = itemRepository.findAll(specification, pageable);
        Map<UUID, List<BarcodeResponse>> barcodes = loadBarcodes(items.getContent());
        return items.map(item -> response(item, barcodes.getOrDefault(item.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(UUID id) {
        InventoryItem item = find(id);
        return response(item, barcodeResponses(barcodeRepository.findByItemIdOrderByPrimaryDescCreatedAtAsc(id)));
    }

    @Transactional(readOnly = true)
    public InventoryResponse findByBarcode(String rawBarcode, UUID warehouseId) {
        if (warehouseId == null) {
            List<ItemBarcode> matches = barcodeRepository.findByBarcode(rawBarcode.trim().toUpperCase());
            if (matches.isEmpty()) {
                throw new NotFoundException("No inventory item matches that barcode.");
            }
            if (matches.size() > 1) {
                throw new ConflictException("Barcode exists in multiple warehouses; select a warehouse.");
            }
            return get(matches.get(0).getItem().getId());
        }
        ItemBarcode barcode = barcodeRepository
                .findByBarcodeAndItemWarehouseId(rawBarcode.trim().toUpperCase(), warehouseId)
                .orElseThrow(() -> new NotFoundException("No inventory item matches that barcode."));
        return get(barcode.getItem().getId());
    }

    @Transactional
    public InventoryResponse create(String idempotencyKey, InventoryCreateRequest request) {
        var replay = idempotencyService.replay(idempotencyKey, "inventory.create", request,
                InventoryResponse.class);
        if (replay.isPresent()) {
            return replay.get();
        }
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new NotFoundException("Warehouse was not found."));
        if (!warehouse.isActive()) {
            throw new BusinessRuleException("Cannot create inventory in an inactive warehouse.");
        }
        String sku = request.sku().trim().toUpperCase();
        if (itemRepository.existsByWarehouseIdAndSku(warehouse.getId(), sku)) {
            throw new ConflictException("SKU already exists in this warehouse.");
        }
        List<BarcodeRequest> barcodeRequests = normalizedBarcodes(request.barcode(), request.symbology(),
                request.aliases(), warehouse.getId(), null);
        String primary = barcodeRequests.get(0).barcode();
        InventoryItem item = itemRepository.saveAndFlush(new InventoryItem(warehouse, sku, primary,
                request.name(), request.quantity(), request.reorderPoint()));
        List<ItemBarcode> barcodes = saveBarcodes(item, barcodeRequests);
        movementRepository.save(new StockMovement(item, null, MovementType.INITIAL, request.quantity(),
                0, request.quantity(), "Initial stock", "ITEM", item.getId().toString(),
                requestContext.actor(), idempotencyKey));
        InventoryResponse result = response(item, barcodeResponses(barcodes));
        emit(item, "inventory.item-created");
        auditService.record("InventoryItem", item.getId(), "CREATE", "Item created", null, result);
        idempotencyService.store(idempotencyKey, "inventory.create", request, item.getId(), result, 201);
        return result;
    }

    @Transactional
    public InventoryResponse update(String idempotencyKey, UUID id, InventoryUpdateRequest request) {
        String operation = "inventory.update." + id;
        var replay = idempotencyService.replay(idempotencyKey, operation, request, InventoryResponse.class);
        if (replay.isPresent()) {
            return replay.get();
        }
        InventoryItem item = lock(id);
        if (item.getVersion() != request.version()) {
            throw new ConflictException("Inventory changed since it was read. Refresh and retry.");
        }
        InventoryResponse before = response(item,
                barcodeResponses(barcodeRepository.findByItemIdOrderByPrimaryDescCreatedAtAsc(id)));
        List<BarcodeRequest> requested = normalizedBarcodes(request.barcode(), request.symbology(),
                request.aliases(), item.getWarehouse().getId(), item.getId());
        item.updateMetadata(requested.get(0).barcode(), request.name(), request.reorderPoint());
        if (request.active()) {
            item.activate();
        } else {
            if (reservationRepository.existsByItemIdAndStatus(id,
                    com.example.inventory.stock.domain.ReservationStatus.ACTIVE)) {
                throw new BusinessRuleException("Release or fulfill active reservations first.");
            }
            item.deactivate();
        }
        itemRepository.saveAndFlush(item);
        List<ItemBarcode> existing = barcodeRepository.findByItemIdOrderByPrimaryDescCreatedAtAsc(id);
        barcodeRepository.deleteAll(existing);
        barcodeRepository.flush();
        List<ItemBarcode> savedBarcodes = saveBarcodes(item, requested);
        InventoryResponse result = response(item, barcodeResponses(savedBarcodes));
        emit(item, "inventory.item-updated");
        auditService.record("InventoryItem", id, "UPDATE", "Item metadata updated", before, result);
        idempotencyService.store(idempotencyKey, operation, request, id, result, 200);
        return result;
    }

    @Transactional
    public void deactivate(UUID id, ItemDeactivateRequest request) {
        InventoryItem item = lock(id);
        if (item.getVersion() != request.version()) {
            throw new ConflictException("Inventory changed since it was read. Refresh and retry.");
        }
        InventoryResponse before = response(item,
                barcodeResponses(barcodeRepository.findByItemIdOrderByPrimaryDescCreatedAtAsc(id)));
        if (reservationRepository.existsByItemIdAndStatus(id,
                com.example.inventory.stock.domain.ReservationStatus.ACTIVE)) {
            throw new BusinessRuleException("Release or fulfill active reservations first.");
        }
        item.deactivate();
        itemRepository.saveAndFlush(item);
        emit(item, "inventory.item-deactivated");
        auditService.record("InventoryItem", id, "DEACTIVATE", request.reason(), before,
                response(item, before.barcodes()));
    }

    public InventoryItem find(UUID id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Inventory item was not found."));
    }

    private InventoryItem lock(UUID id) {
        return itemRepository.lockById(id)
                .orElseThrow(() -> new NotFoundException("Inventory item was not found."));
    }

    private List<BarcodeRequest> normalizedBarcodes(String primaryBarcode,
                                                     com.example.inventory.stock.domain.BarcodeSymbology symbology,
                                                     List<BarcodeRequest> aliases, UUID warehouseId,
                                                     UUID currentItemId) {
        List<BarcodeRequest> all = new ArrayList<>();
        all.add(new BarcodeRequest(barcodeValidator.validateAndNormalize(primaryBarcode, symbology),
                symbology, true));
        for (BarcodeRequest alias : aliases) {
            if (alias.primary()) {
                throw new BusinessRuleException("Only the top-level barcode can be primary.");
            }
            all.add(new BarcodeRequest(barcodeValidator.validateAndNormalize(alias.barcode(), alias.symbology()),
                    alias.symbology(), false));
        }
        Set<String> unique = all.stream().map(BarcodeRequest::barcode).collect(Collectors.toSet());
        if (unique.size() != all.size()) {
            throw new BusinessRuleException("Duplicate barcodes are not allowed on an item.");
        }
        for (BarcodeRequest barcode : all) {
            barcodeRepository.findByBarcodeAndItemWarehouseId(barcode.barcode(), warehouseId)
                    .filter(existing -> currentItemId == null || !existing.getItem().getId().equals(currentItemId))
                    .ifPresent(existing -> {
                        throw new ConflictException("Barcode already belongs to another item in this warehouse.");
                    });
        }
        return all;
    }

    private List<ItemBarcode> saveBarcodes(InventoryItem item, List<BarcodeRequest> requests) {
        return barcodeRepository.saveAll(requests.stream()
                .map(request -> new ItemBarcode(item, request.barcode(), request.symbology(), request.primary()))
                .toList());
    }

    private Map<UUID, List<BarcodeResponse>> loadBarcodes(List<InventoryItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<BarcodeResponse>> result = new LinkedHashMap<>();
        barcodeRepository.findByItemIdIn(items.stream().map(InventoryItem::getId).toList())
                .forEach(barcode -> result.computeIfAbsent(barcode.getItem().getId(), ignored -> new ArrayList<>())
                        .add(barcodeResponse(barcode)));
        result.values().forEach(values -> values.sort((left, right) -> Boolean.compare(right.primary(), left.primary())));
        return result;
    }

    private List<BarcodeResponse> barcodeResponses(List<ItemBarcode> barcodes) {
        return barcodes.stream().map(this::barcodeResponse).toList();
    }

    private BarcodeResponse barcodeResponse(ItemBarcode barcode) {
        return new BarcodeResponse(barcode.getId(), barcode.getBarcode(), barcode.getSymbology(),
                barcode.isPrimary());
    }

    public InventoryResponse response(InventoryItem item) {
        return response(item, barcodeResponses(barcodeRepository
                .findByItemIdOrderByPrimaryDescCreatedAtAsc(item.getId())));
    }

    private InventoryResponse response(InventoryItem item, List<BarcodeResponse> barcodes) {
        return new InventoryResponse(item.getId(), item.getSku(), item.getBarcode(), List.copyOf(barcodes),
                item.getName(), item.getQuantity(), item.getReservedQuantity(), item.getAvailableQuantity(),
                item.getReorderPoint(), item.getAvailableQuantity() <= item.getReorderPoint(), item.isActive(),
                item.getWarehouse().getId(), item.getWarehouse().getCode(), item.getVersion(), item.getCreatedAt(),
                item.getUpdatedAt());
    }

    public void emit(InventoryItem item, String type) {
        outboxService.append(new StockChangedEvent(UUID.randomUUID(), type, 1, item.getId(),
                item.getWarehouse().getId(), item.getSku(), item.getQuantity(), item.getReservedQuantity(),
                item.getAvailableQuantity(), item.getReorderPoint(),
                item.getAvailableQuantity() <= item.getReorderPoint(), Instant.now(), requestContext.actor(),
                requestContext.correlationId()));
    }
}
