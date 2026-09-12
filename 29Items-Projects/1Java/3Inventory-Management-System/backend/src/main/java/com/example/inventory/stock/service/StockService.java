package com.example.inventory.stock.service;

import com.example.inventory.audit.AuditService;
import com.example.inventory.common.error.BusinessRuleException;
import com.example.inventory.common.error.ConflictException;
import com.example.inventory.common.error.NotFoundException;
import com.example.inventory.common.web.RequestContext;
import com.example.inventory.idempotency.IdempotencyService;
import com.example.inventory.inventory.domain.InventoryItem;
import com.example.inventory.inventory.domain.Warehouse;
import com.example.inventory.inventory.dto.InventoryResponse;
import com.example.inventory.inventory.repository.InventoryItemRepository;
import com.example.inventory.inventory.repository.WarehouseRepository;
import com.example.inventory.inventory.service.InventoryService;
import com.example.inventory.stock.domain.ItemBarcode;
import com.example.inventory.stock.domain.MovementType;
import com.example.inventory.stock.domain.Reservation;
import com.example.inventory.stock.domain.StockMovement;
import com.example.inventory.stock.dto.AdjustmentRequest;
import com.example.inventory.stock.dto.BulkAdjustmentRequest;
import com.example.inventory.stock.dto.BulkAdjustmentResponse;
import com.example.inventory.stock.dto.QuantityCommandRequest;
import com.example.inventory.stock.dto.ReservationCreateRequest;
import com.example.inventory.stock.dto.ReservationResponse;
import com.example.inventory.stock.dto.StockMovementResponse;
import com.example.inventory.stock.dto.TransferRequest;
import com.example.inventory.stock.dto.TransferResponse;
import com.example.inventory.stock.repository.ItemBarcodeRepository;
import com.example.inventory.stock.repository.ReservationRepository;
import com.example.inventory.stock.repository.StockMovementRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final InventoryItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final ItemBarcodeRepository barcodeRepository;
    private final StockMovementRepository movementRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryService inventoryService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final RequestContext requestContext;

    public StockService(InventoryItemRepository itemRepository,
                        WarehouseRepository warehouseRepository,
                        ItemBarcodeRepository barcodeRepository,
                        StockMovementRepository movementRepository,
                        ReservationRepository reservationRepository,
                        InventoryService inventoryService,
                        IdempotencyService idempotencyService,
                        AuditService auditService,
                        RequestContext requestContext) {
        this.itemRepository = itemRepository;
        this.warehouseRepository = warehouseRepository;
        this.barcodeRepository = barcodeRepository;
        this.movementRepository = movementRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryService = inventoryService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.requestContext = requestContext;
    }

    @Transactional
    public InventoryResponse adjust(String key, UUID itemId, AdjustmentRequest request) {
        return mutate(key, "stock.adjust." + itemId, itemId, request, MovementType.ADJUSTMENT,
                request.delta(), request.reason(), "ADJUSTMENT", request.reference(),
                item -> item.adjust(request.delta()));
    }

    @Transactional
    public InventoryResponse receive(String key, UUID itemId, QuantityCommandRequest request) {
        return mutate(key, "stock.receive." + itemId, itemId, request, MovementType.RECEIPT,
                request.quantity(), request.reason(), "RECEIPT", request.reference(),
                item -> item.receive(request.quantity()));
    }

    @Transactional
    public InventoryResponse ship(String key, UUID itemId, QuantityCommandRequest request) {
        return mutate(key, "stock.ship." + itemId, itemId, request, MovementType.SHIPMENT,
                -request.quantity(), request.reason(), "SHIPMENT", request.reference(),
                item -> item.ship(request.quantity()));
    }

    @Transactional
    public BulkAdjustmentResponse bulkAdjust(String key, BulkAdjustmentRequest request) {
        var replay = idempotencyService.replay(key, "stock.bulk-adjust", request, BulkAdjustmentResponse.class);
        if (replay.isPresent()) {
            return replay.get();
        }
        List<BulkAdjustmentRequest.Entry> entries = request.entries().stream()
                .sorted(Comparator.comparing(BulkAdjustmentRequest.Entry::itemId))
                .toList();
        List<InventoryResponse> responses = new ArrayList<>();
        for (BulkAdjustmentRequest.Entry entry : entries) {
            InventoryItem item = lock(entry.itemId());
            InventoryResponse before = inventoryService.response(item);
            long quantityBefore = item.adjust(entry.delta());
            itemRepository.saveAndFlush(item);
            movement(item, null, MovementType.ADJUSTMENT, entry.delta(), quantityBefore,
                    entry.reason(), "BULK_ADJUSTMENT", entry.reference(), key);
            InventoryResponse after = inventoryService.response(item);
            inventoryService.emit(item, "inventory.stock-changed");
            auditService.record("InventoryItem", item.getId(), "BULK_ADJUSTMENT",
                    entry.reason(), before, after);
            responses.add(after);
        }
        BulkAdjustmentResponse result = new BulkAdjustmentResponse(responses);
        idempotencyService.store(key, "stock.bulk-adjust", request, null, result, 200);
        return result;
    }

    @Transactional
    public ReservationResponse reserve(String key, UUID itemId, ReservationCreateRequest request) {
        String operation = "stock.reserve." + itemId;
        var replay = idempotencyService.replay(key, operation, request, ReservationResponse.class);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (reservationRepository.existsByExternalReference(request.externalReference())) {
            throw new ConflictException("Reservation reference already exists.");
        }
        InventoryItem item = lock(itemId);
        InventoryResponse before = inventoryService.response(item);
        item.reserve(request.quantity());
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(item, request.quantity(),
                request.externalReference(), request.reason(), requestContext.actor()));
        itemRepository.saveAndFlush(item);
        movement(item, null, MovementType.RESERVATION, 0, item.getQuantity(), request.reason(),
                "RESERVATION", reservation.getId().toString(), key);
        ReservationResponse result = reservationResponse(reservation);
        inventoryService.emit(item, "inventory.reserved");
        auditService.record("InventoryItem", itemId, "RESERVE", request.reason(), before,
                inventoryService.response(item));
        idempotencyService.store(key, operation, request, reservation.getId(), result, 201);
        return result;
    }

    @Transactional
    public ReservationResponse release(String key, UUID reservationId) {
        return completeReservation(key, reservationId, false);
    }

    @Transactional
    public ReservationResponse fulfill(String key, UUID reservationId) {
        return completeReservation(key, reservationId, true);
    }

    @Transactional
    public TransferResponse transfer(String key, TransferRequest request) {
        var replay = idempotencyService.replay(key, "stock.transfer", request, TransferResponse.class);
        if (replay.isPresent()) {
            return replay.get();
        }
        InventoryItem sourceView = inventoryService.find(request.sourceItemId());
        Warehouse destinationWarehouse = warehouseRepository.findById(request.destinationWarehouseId())
                .orElseThrow(() -> new NotFoundException("Destination warehouse was not found."));
        if (!destinationWarehouse.isActive()) {
            throw new BusinessRuleException("Destination warehouse is inactive.");
        }
        if (sourceView.getWarehouse().getId().equals(destinationWarehouse.getId())) {
            throw new BusinessRuleException("Source and destination warehouses must differ.");
        }
        InventoryItem destinationView = itemRepository
                .findByWarehouseIdAndSku(destinationWarehouse.getId(), sourceView.getSku()).orElse(null);
        InventoryItem source;
        InventoryItem destination;
        if (destinationView == null) {
            source = lock(sourceView.getId());
            destination = createDestinationItem(source, destinationWarehouse);
        } else {
            List<UUID> lockOrder = List.of(sourceView.getId(), destinationView.getId()).stream()
                    .sorted().toList();
            InventoryItem first = lock(lockOrder.get(0));
            InventoryItem second = lock(lockOrder.get(1));
            source = first.getId().equals(sourceView.getId()) ? first : second;
            destination = first.getId().equals(destinationView.getId()) ? first : second;
        }
        InventoryResponse sourceBefore = inventoryService.response(source);
        InventoryResponse destinationBefore = inventoryService.response(destination);
        long sourceQuantityBefore = source.ship(request.quantity());
        long destinationQuantityBefore = destination.receive(request.quantity());
        itemRepository.saveAndFlush(source);
        itemRepository.saveAndFlush(destination);
        UUID transferId = UUID.randomUUID();
        String reference = request.reference() == null || request.reference().isBlank()
                ? transferId.toString() : request.reference();
        movement(source, destination, MovementType.TRANSFER_OUT, -request.quantity(), sourceQuantityBefore,
                request.reason(), "TRANSFER", reference, key);
        movement(destination, source, MovementType.TRANSFER_IN, request.quantity(), destinationQuantityBefore,
                request.reason(), "TRANSFER", reference, key);
        inventoryService.emit(source, "inventory.stock-transferred");
        inventoryService.emit(destination, "inventory.stock-transferred");
        auditService.record("InventoryItem", source.getId(), "TRANSFER_OUT", request.reason(), sourceBefore,
                inventoryService.response(source));
        auditService.record("InventoryItem", destination.getId(), "TRANSFER_IN", request.reason(),
                destinationBefore, inventoryService.response(destination));
        TransferResponse result = new TransferResponse(transferId, source.getId(), destination.getId(),
                request.quantity(), source.getQuantity(), destination.getQuantity());
        idempotencyService.store(key, "stock.transfer", request, transferId, result, 201);
        return result;
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> movements(UUID itemId, Pageable pageable) {
        inventoryService.find(itemId);
        return movementRepository.findByItemIdOrderByOccurredAtDesc(itemId, pageable).map(this::movementResponse);
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> reservations(UUID itemId, Pageable pageable) {
        inventoryService.find(itemId);
        return reservationRepository.findByItemId(itemId, pageable).map(this::reservationResponse);
    }

    private InventoryResponse mutate(String key, String operation, UUID itemId, Object request,
                                     MovementType type, long delta, String reason, String referenceType,
                                     String referenceId, Consumer<InventoryItem> mutation) {
        var replay = idempotencyService.replay(key, operation, request, InventoryResponse.class);
        if (replay.isPresent()) {
            return replay.get();
        }
        InventoryItem item = lock(itemId);
        InventoryResponse before = inventoryService.response(item);
        long quantityBefore = item.getQuantity();
        mutation.accept(item);
        itemRepository.saveAndFlush(item);
        movement(item, null, type, delta, quantityBefore, reason, referenceType, referenceId, key);
        InventoryResponse result = inventoryService.response(item);
        inventoryService.emit(item, "inventory.stock-changed");
        auditService.record("InventoryItem", itemId, type.name(), reason, before, result);
        idempotencyService.store(key, operation, request, itemId, result, 200);
        return result;
    }

    private ReservationResponse completeReservation(String key, UUID reservationId, boolean fulfill) {
        String operation = (fulfill ? "stock.fulfill." : "stock.release.") + reservationId;
        MapRequest request = new MapRequest(reservationId, fulfill);
        var replay = idempotencyService.replay(key, operation, request, ReservationResponse.class);
        if (replay.isPresent()) {
            return replay.get();
        }
        Reservation reservation = reservationRepository.lockById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation was not found."));
        InventoryItem item = lock(reservation.getItem().getId());
        InventoryResponse before = inventoryService.response(item);
        long quantityBefore = item.getQuantity();
        MovementType movementType;
        long delta;
        if (fulfill) {
            item.fulfillReservation(reservation.getQuantity());
            reservation.fulfill();
            movementType = MovementType.RESERVATION_FULFILLMENT;
            delta = -reservation.getQuantity();
        } else {
            item.releaseReservation(reservation.getQuantity());
            reservation.release();
            movementType = MovementType.RESERVATION_RELEASE;
            delta = 0;
        }
        itemRepository.saveAndFlush(item);
        reservationRepository.saveAndFlush(reservation);
        movement(item, null, movementType, delta, quantityBefore, reservation.getReason(),
                "RESERVATION", reservationId.toString(), key);
        ReservationResponse result = reservationResponse(reservation);
        inventoryService.emit(item, fulfill ? "inventory.reservation-fulfilled" : "inventory.reservation-released");
        auditService.record("InventoryItem", item.getId(), movementType.name(), reservation.getReason(), before,
                inventoryService.response(item));
        idempotencyService.store(key, operation, request, reservationId, result, 200);
        return result;
    }

    private InventoryItem createDestinationItem(InventoryItem source, Warehouse warehouse) {
        InventoryItem destination = itemRepository.saveAndFlush(new InventoryItem(warehouse, source.getSku(),
                source.getBarcode(), source.getName(), 0, source.getReorderPoint()));
        List<ItemBarcode> sourceBarcodes = barcodeRepository
                .findByItemIdOrderByPrimaryDescCreatedAtAsc(source.getId());
        barcodeRepository.saveAll(sourceBarcodes.stream()
                .map(barcode -> new ItemBarcode(destination, barcode.getBarcode(), barcode.getSymbology(),
                        barcode.isPrimary()))
                .toList());
        movement(destination, null, MovementType.INITIAL, 0, 0, "Created by transfer", "ITEM",
                destination.getId().toString(), null);
        return destination;
    }

    private InventoryItem lock(UUID id) {
        return itemRepository.lockById(id)
                .orElseThrow(() -> new NotFoundException("Inventory item was not found."));
    }

    private void movement(InventoryItem item, InventoryItem relatedItem, MovementType type, long delta,
                          long before, String reason, String referenceType, String referenceId, String key) {
        movementRepository.save(new StockMovement(item, relatedItem, type, delta, before, item.getQuantity(),
                reason, referenceType, referenceId, requestContext.actor(), key));
    }

    private ReservationResponse reservationResponse(Reservation reservation) {
        return new ReservationResponse(reservation.getId(), reservation.getItem().getId(),
                reservation.getQuantity(), reservation.getStatus(), reservation.getExternalReference(),
                reservation.getReason(), reservation.getActor(), reservation.getVersion(),
                reservation.getCreatedAt(), reservation.getUpdatedAt());
    }

    private StockMovementResponse movementResponse(StockMovement movement) {
        return new StockMovementResponse(movement.getId(), movement.getItem().getId(),
                movement.getRelatedItem() == null ? null : movement.getRelatedItem().getId(), movement.getType(),
                movement.getQuantityDelta(), movement.getQuantityBefore(), movement.getQuantityAfter(),
                movement.getReason(), movement.getReferenceType(), movement.getReferenceId(), movement.getActor(),
                movement.getOccurredAt());
    }

    private record MapRequest(UUID reservationId, boolean fulfill) {
    }
}
