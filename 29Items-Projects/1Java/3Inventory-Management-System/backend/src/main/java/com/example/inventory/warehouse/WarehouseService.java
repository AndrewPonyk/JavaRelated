package com.example.inventory.warehouse;

import com.example.inventory.audit.AuditService;
import com.example.inventory.common.error.BusinessRuleException;
import com.example.inventory.common.error.ConflictException;
import com.example.inventory.common.error.NotFoundException;
import com.example.inventory.inventory.domain.Warehouse;
import com.example.inventory.inventory.repository.InventoryItemRepository;
import com.example.inventory.inventory.repository.WarehouseRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository itemRepository;
    private final AuditService auditService;

    public WarehouseService(WarehouseRepository warehouseRepository,
                            InventoryItemRepository itemRepository,
                            AuditService auditService) {
        this.warehouseRepository = warehouseRepository;
        this.itemRepository = itemRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<WarehouseResponse> list(Pageable pageable) {
        return warehouseRepository.findAll(pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(UUID id) {
        return response(find(id));
    }

    @Transactional
    public WarehouseResponse create(WarehouseCreateRequest request) {
        String code = request.code().trim().toUpperCase();
        if (warehouseRepository.existsByCode(code)) {
            throw new ConflictException("Warehouse code already exists.");
        }
        Warehouse warehouse = warehouseRepository.saveAndFlush(new Warehouse(code, request.name()));
        WarehouseResponse result = response(warehouse);
        auditService.record("Warehouse", warehouse.getId(), "CREATE", "Warehouse created", null, result);
        return result;
    }

    @Transactional
    public WarehouseResponse update(UUID id, WarehouseUpdateRequest request) {
        Warehouse warehouse = find(id);
        if (warehouse.getVersion() != request.version()) {
            throw new ConflictException("Warehouse changed since it was read. Refresh and retry.");
        }
        WarehouseResponse before = response(warehouse);
        warehouse.update(request.name());
        if (request.active()) {
            warehouse.activate();
        } else {
            ensureCanDeactivate(warehouse);
            warehouse.deactivate();
        }
        Warehouse saved = warehouseRepository.saveAndFlush(warehouse);
        WarehouseResponse result = response(saved);
        auditService.record("Warehouse", id, "UPDATE", "Warehouse updated", before, result);
        return result;
    }

    @Transactional
    public void deactivate(UUID id) {
        Warehouse warehouse = find(id);
        ensureCanDeactivate(warehouse);
        WarehouseResponse before = response(warehouse);
        warehouse.deactivate();
        warehouseRepository.saveAndFlush(warehouse);
        auditService.record("Warehouse", id, "DEACTIVATE", "Warehouse deactivated", before,
                response(warehouse));
    }

    private void ensureCanDeactivate(Warehouse warehouse) {
        if (itemRepository.existsByWarehouseIdAndActiveTrue(warehouse.getId())) {
            throw new BusinessRuleException("Deactivate or transfer all active inventory items first.");
        }
    }

    private Warehouse find(UUID id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Warehouse was not found."));
    }

    private WarehouseResponse response(Warehouse warehouse) {
        return new WarehouseResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName(),
                warehouse.isActive(), warehouse.getVersion(), warehouse.getCreatedAt(), warehouse.getUpdatedAt());
    }
}

