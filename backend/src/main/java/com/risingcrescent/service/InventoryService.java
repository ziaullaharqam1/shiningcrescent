package com.risingcrescent.service;

import com.risingcrescent.audit.AuditService;
import com.risingcrescent.domain.entity.InventoryLot;
import com.risingcrescent.domain.entity.QualityInspection;
import com.risingcrescent.domain.entity.SalesOrder;
import com.risingcrescent.domain.entity.SalesOrderLine;
import com.risingcrescent.domain.enums.InspectionResult;
import com.risingcrescent.domain.enums.LotStatus;
import com.risingcrescent.event.DomainEvent;
import com.risingcrescent.event.EventPublisher;
import com.risingcrescent.repo.InventoryLotRepository;
import com.risingcrescent.repo.ProductRepository;
import com.risingcrescent.repo.QualityInspectionRepository;
import com.risingcrescent.repo.SupplierRepository;
import com.risingcrescent.repo.WarehouseRepository;
import com.risingcrescent.workflow.WorkflowService;
import com.risingcrescent.domain.enums.WorkflowEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryLotRepository lots;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final SupplierRepository suppliers;
    private final QualityInspectionRepository inspections;
    private final WorkflowService workflow;
    private final EventPublisher events;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public Map<String, Object> lookups() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("products", products.findAll().stream().map(p -> Map.of(
                "id", p.getId(),
                "sku", p.getSku(),
                "name", p.getName(),
                "grade", p.getGrade() == null ? "" : p.getGrade()
        )).toList());
        m.put("warehouses", warehouses.findAll().stream().map(w -> Map.of(
                "id", w.getId(),
                "name", w.getName(),
                "code", w.getCode() == null ? "" : w.getCode()
        )).toList());
        m.put("suppliers", suppliers.findAll().stream().map(s -> Map.of(
                "id", s.getId(),
                "name", s.getName()
        )).toList());
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> all() {
        return lots.findAll().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> forSupplier(String username) {
        return lots.findBySupplier_PortalUser_Username(username).stream().map(this::view).toList();
    }

    @Transactional
    public void allocate(SalesOrder order) {
        for (SalesOrderLine line : order.getLines()) {
            BigDecimal remaining = line.getQty();
            List<InventoryLot> fefo = lots.findByProduct_IdAndStatusOrderByExpiryOnAsc(line.getProduct().getId(), LotStatus.AVAILABLE);
            StringBuilder allocated = new StringBuilder();
            for (InventoryLot lot : fefo) {
                if (remaining.signum() <= 0) {
                    break;
                }
                BigDecimal take = remaining.min(lot.getAvailableQty());
                if (take.signum() <= 0) {
                    continue;
                }
                lot.setAvailableQty(lot.getAvailableQty().subtract(take));
                lot.setReservedQty(lot.getReservedQty().add(take));
                remaining = remaining.subtract(take);
                if (!allocated.isEmpty()) {
                    allocated.append(",");
                }
                allocated.append(lot.getLotCode()).append(":").append(take.toPlainString());
                lots.save(lot);
                if (lot.getAvailableQty().compareTo(new BigDecimal("5")) < 0) {
                    events.publish(DomainEvent.of("rc.inventory", "LOW_STOCK", line.getProduct().getSku(),
                            Map.of("product", line.getProduct().getName(), "lot", lot.getLotCode())));
                }
            }
            if (remaining.signum() > 0) {
                throw new IllegalStateException("Not enough stock for " + line.getProduct().getName()
                        + ". Short by " + remaining + " " + line.getProduct().getUom() + ".");
            }
            line.setAllocatedLot(allocated.toString());
        }
        audit.record(order.getLastActor(), "STOCK_ALLOCATE", "SALES_ORDER", order.getOrderNo(), "FEFO pick");
    }

    @Transactional
    public void release(SalesOrder order) {
        for (SalesOrderLine line : order.getLines()) {
            if (line.getAllocatedLot() == null || line.getAllocatedLot().isBlank()) {
                continue;
            }
            for (String part : line.getAllocatedLot().split(",")) {
                String[] bits = part.split(":");
                lots.findAll().stream().filter(l -> l.getLotCode().equals(bits[0])).findFirst().ifPresent(lot -> {
                    BigDecimal qty = new BigDecimal(bits[1]);
                    lot.setReservedQty(lot.getReservedQty().subtract(qty));
                    lot.setAvailableQty(lot.getAvailableQty().add(qty));
                    lots.save(lot);
                });
            }
            line.setAllocatedLot(null);
        }
    }

    @Transactional
    public void shipConsume(SalesOrder order) {
        for (SalesOrderLine line : order.getLines()) {
            if (line.getAllocatedLot() == null) {
                continue;
            }
            for (String part : line.getAllocatedLot().split(",")) {
                String[] bits = part.split(":");
                lots.findAll().stream().filter(l -> l.getLotCode().equals(bits[0])).findFirst().ifPresent(lot -> {
                    BigDecimal qty = new BigDecimal(bits[1]);
                    lot.setReservedQty(lot.getReservedQty().subtract(qty));
                    lots.save(lot);
                });
            }
        }
    }

    public Map<String, Object> view(InventoryLot lot) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", lot.getId());
        m.put("lotCode", lot.getLotCode());
        m.put("sku", lot.getProduct().getSku());
        m.put("product", lot.getProduct().getName());
        m.put("warehouse", lot.getWarehouse().getName());
        m.put("availableQty", lot.getAvailableQty());
        m.put("reservedQty", lot.getReservedQty());
        m.put("receivedQty", lot.getReceivedQty());
        m.put("status", lot.getStatus());
        m.put("grade", lot.getGrade());
        m.put("expiryOn", lot.getExpiryOn());
        m.put("moisturePct", lot.getMoisturePct());
        m.put("supplier", lot.getSupplier() == null ? "" : lot.getSupplier().getName());
        return m;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, String actor) {
        var product = products.findById(Long.valueOf(body.get("productId").toString()))
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        var warehouse = warehouses.findById(Long.valueOf(body.get("warehouseId").toString()))
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found"));
        var supplier = body.get("supplierId") == null || body.get("supplierId").toString().isBlank()
                ? null
                : suppliers.findById(Long.valueOf(body.get("supplierId").toString())).orElse(null);
        BigDecimal qty = new BigDecimal(body.getOrDefault("qty", "0").toString());
        if (qty.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        LotStatus status = body.get("status") == null || body.get("status").toString().isBlank()
                ? LotStatus.AVAILABLE
                : LotStatus.valueOf(body.get("status").toString());
        String lotCode = body.get("lotCode") == null || body.get("lotCode").toString().isBlank()
                ? "LOT-" + Instant.now().toEpochMilli()
                : body.get("lotCode").toString().trim();
        LocalDate expiry = body.get("expiryOn") == null || body.get("expiryOn").toString().isBlank()
                ? LocalDate.now().plusDays(product.getShelfLifeDays() == null ? 180 : product.getShelfLifeDays())
                : LocalDate.parse(body.get("expiryOn").toString());
        boolean quarantine = status == LotStatus.QUARANTINE;
        InventoryLot lot = InventoryLot.builder()
                .lotCode(lotCode)
                .product(product)
                .warehouse(warehouse)
                .supplier(supplier)
                .receivedQty(qty)
                .availableQty(quarantine ? BigDecimal.ZERO : qty)
                .reservedQty(BigDecimal.ZERO)
                .receivedOn(LocalDate.now())
                .expiryOn(expiry)
                .grade(body.get("grade") == null ? product.getGrade() : body.get("grade").toString())
                .status(status)
                .build();
        lots.save(lot);
        if (quarantine) {
            QualityInspection qi = inspections.save(QualityInspection.builder()
                    .inspectionNo("QC-" + Instant.now().toEpochMilli())
                    .lot(lot)
                    .result(InspectionResult.PENDING)
                    .build());
            workflow.step(WorkflowEntity.QUALITY_INSPECTION, qi.getInspectionNo(), null, "PENDING", actor, "Manual lot intake");
        }
        audit.record(actor, "LOT_CREATE", "INVENTORY", lot.getLotCode(), status.name() + " " + qty);
        events.publish(DomainEvent.of("rc.inventory", "LOT_CREATED", lot.getLotCode(),
                Map.of("product", product.getName(), "qty", qty.toPlainString())));
        return view(lot);
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body, String actor) {
        InventoryLot lot = lots.findById(id).orElseThrow(() -> new IllegalArgumentException("Lot not found"));
        if (body.get("availableQty") != null) {
            lot.setAvailableQty(new BigDecimal(body.get("availableQty").toString()));
        }
        if (body.get("reservedQty") != null) {
            lot.setReservedQty(new BigDecimal(body.get("reservedQty").toString()));
        }
        if (body.get("receivedQty") != null) {
            lot.setReceivedQty(new BigDecimal(body.get("receivedQty").toString()));
        }
        if (body.get("status") != null && !body.get("status").toString().isBlank()) {
            lot.setStatus(LotStatus.valueOf(body.get("status").toString()));
        }
        if (body.get("grade") != null) {
            lot.setGrade(body.get("grade").toString());
        }
        if (body.get("expiryOn") != null && !body.get("expiryOn").toString().isBlank()) {
            lot.setExpiryOn(java.time.LocalDate.parse(body.get("expiryOn").toString()));
        }
        if (body.get("moisturePct") != null && !body.get("moisturePct").toString().isBlank()) {
            lot.setMoisturePct(new BigDecimal(body.get("moisturePct").toString()));
        }
        lots.save(lot);
        audit.record(actor, "LOT_UPDATE", "INVENTORY", lot.getLotCode(), String.valueOf(lot.getStatus()));
        return view(lot);
    }
}
