package com.risingcrescent.service;

import com.risingcrescent.audit.AuditService;
import com.risingcrescent.domain.entity.*;
import com.risingcrescent.domain.enums.*;
import com.risingcrescent.event.DomainEvent;
import com.risingcrescent.event.EventPublisher;
import com.risingcrescent.repo.*;
import com.risingcrescent.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProcurementService {
    private final PurchaseOrderRepository pos;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final InventoryLotRepository lots;
    private final QualityInspectionRepository inspections;
    private final EventPublisher events;
    private final AuditService audit;
    private final WorkflowService workflow;

    @Transactional
    public Map<String, Object> createPo(String actor, Map<String, Object> body) {
        Long supplierId = Long.valueOf(body.get("supplierId").toString());
        Long warehouseId = Long.valueOf(body.get("warehouseId").toString());
        PurchaseOrder po = PurchaseOrder.builder()
                .poNo("PO-" + Instant.now().toEpochMilli())
                .supplier(suppliers.findById(supplierId).orElseThrow())
                .warehouse(warehouses.findById(warehouseId).orElseThrow())
                .status(PurchaseStatus.DRAFT)
                .expectedDate(LocalDate.now().plusDays(7))
                .notes((String) body.get("notes"))
                .createdBy(actor)
                .build();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            Product product = products.findById(Long.valueOf(line.get("productId").toString())).orElseThrow();
            BigDecimal qty = new BigDecimal(line.get("qty").toString());
            BigDecimal cost = new BigDecimal(line.get("unitCost").toString());
            po.getLines().add(PurchaseOrderLine.builder()
                    .purchaseOrder(po).product(product).qty(qty).unitCost(cost).build());
            total = total.add(qty.multiply(cost));
        }
        po.setTotal(total);
        pos.save(po);
        workflow.step(WorkflowEntity.PURCHASE_ORDER, po.getPoNo(), null, "DRAFT", actor, "Created");
        audit.record(actor, "PO_CREATE", "PURCHASE_ORDER", po.getPoNo(), "Draft purchase order");
        return view(po);
    }

    @Transactional
    public Map<String, Object> seedOpeningStockForProduct(Product product, String actor) {
        Supplier supplier = suppliers.findAll().stream()
                .filter(Supplier::isActive)
                .sorted((a, b) -> Boolean.compare(b.isPreferred(), a.isPreferred()))
                .findFirst()
                .orElse(null);
        Warehouse warehouse = warehouses.findAll().stream()
                .filter(Warehouse::isActive)
                .findFirst()
                .orElse(null);
        Map<String, Object> info = new LinkedHashMap<>();
        if (supplier == null || warehouse == null) {
            info.put("seeded", false);
            info.put("seedNote", "Add a supplier and warehouse first — opening PO was not created.");
            return info;
        }
        BigDecimal qty = new BigDecimal("3");
        BigDecimal cost = product.getWholesalePrice() != null ? product.getWholesalePrice()
                : product.getRetailPrice() != null ? product.getRetailPrice()
                : new BigDecimal("10");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supplierId", supplier.getId());
        body.put("warehouseId", warehouse.getId());
        body.put("notes", "Auto opening cover for new SKU " + product.getSku());
        body.put("lines", List.of(Map.of(
                "productId", product.getId(),
                "qty", qty,
                "unitCost", cost
        )));
        Map<String, Object> created = createPo(actor, body);
        Long poId = Long.valueOf(created.get("id").toString());
        movePo(poId, PurchaseStatus.SUBMITTED, actor, "Auto submit opening PO");
        movePo(poId, PurchaseStatus.APPROVED, actor, "Auto approve opening PO");
        movePo(poId, PurchaseStatus.SENT_TO_SUPPLIER, actor, "Auto send opening PO");
        movePo(poId, PurchaseStatus.ACKNOWLEDGED, actor, "Auto acknowledge opening PO");
        Map<String, Object> grn = receive(poId, product.getId(), qty, actor);
        QualityInspection qi = inspections.findByInspectionNo(String.valueOf(grn.get("inspection")))
                .orElseThrow();
        inspect(qi.getId(), InspectionResult.PASSED, null, "Opening stock", "None",
                "Auto-approved QA for new product opening lot", actor);
        InventoryLot lot = lots.findAll().stream()
                .filter(l -> l.getLotCode().equals(String.valueOf(grn.get("lot"))))
                .findFirst()
                .orElse(null);
        info.put("seeded", true);
        info.put("starterPo", created.get("poNo"));
        info.put("starterPoId", poId);
        info.put("supplier", supplier.getName());
        info.put("warehouse", warehouse.getName());
        info.put("openingQty", qty);
        info.put("lotCode", grn.get("lot"));
        info.put("inspectionNo", grn.get("inspection"));
        info.put("lotStatus", lot == null ? LotStatus.AVAILABLE : lot.getStatus());
        info.put("availableQty", lot == null ? qty : lot.getAvailableQty());
        info.put("qa", "PASSED");
        return info;
    }

    @Transactional
    public Map<String, Object> updatePo(Long id, Map<String, Object> body, String actor) {
        PurchaseOrder po = pos.findById(id).orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        if (po.getStatus() == PurchaseStatus.RECEIVED || po.getStatus() == PurchaseStatus.CANCELLED) {
            throw new IllegalArgumentException("This purchase order can no longer be edited.");
        }
        if (body.get("notes") != null) {
            po.setNotes(body.get("notes").toString());
        }
        if (body.get("expectedDate") != null && !body.get("expectedDate").toString().isBlank()) {
            po.setExpectedDate(LocalDate.parse(body.get("expectedDate").toString()));
        }
        if (body.get("supplierId") != null && po.getStatus() == PurchaseStatus.DRAFT) {
            po.setSupplier(suppliers.findById(Long.valueOf(body.get("supplierId").toString())).orElseThrow());
        }
        if (body.get("warehouseId") != null && po.getStatus() == PurchaseStatus.DRAFT) {
            po.setWarehouse(warehouses.findById(Long.valueOf(body.get("warehouseId").toString())).orElseThrow());
        }
        if (body.get("lines") instanceof List<?> raw && po.getStatus() == PurchaseStatus.DRAFT) {
            po.getLines().clear();
            BigDecimal total = BigDecimal.ZERO;
            for (Object item : raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> line = (Map<String, Object>) item;
                Product product = products.findById(Long.valueOf(line.get("productId").toString())).orElseThrow();
                BigDecimal qty = new BigDecimal(line.get("qty").toString());
                BigDecimal cost = new BigDecimal(line.get("unitCost").toString());
                po.getLines().add(PurchaseOrderLine.builder()
                        .purchaseOrder(po).product(product).qty(qty).unitCost(cost).build());
                total = total.add(qty.multiply(cost));
            }
            po.setTotal(total);
        } else if (body.get("qty") != null && po.getLines().size() == 1
                && (po.getStatus() == PurchaseStatus.DRAFT || po.getStatus() == PurchaseStatus.SUBMITTED)) {
            PurchaseOrderLine line = po.getLines().get(0);
            line.setQty(new BigDecimal(body.get("qty").toString()));
            if (body.get("unitCost") != null) {
                line.setUnitCost(new BigDecimal(body.get("unitCost").toString()));
            }
            po.setTotal(line.getQty().multiply(line.getUnitCost()));
        }
        pos.save(po);
        workflow.step(WorkflowEntity.PURCHASE_ORDER, po.getPoNo(), po.getStatus().name(), po.getStatus().name(), actor, "Edited");
        audit.record(actor, "PO_UPDATE", "PURCHASE_ORDER", po.getPoNo(), "Edited purchase order");
        return view(po);
    }

    @Transactional
    public Map<String, Object> approveOrQuarantine(Long id, String action, String actor, String comment) {
        String act = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (act.contains("QUARANTINE") || act.contains("HOLD")) {
            PurchaseOrder po = pos.findById(id).orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
            if (po.getStatus() == PurchaseStatus.DRAFT || po.getStatus() == PurchaseStatus.SUBMITTED) {
                movePo(id, PurchaseStatus.APPROVED, actor, comment == null ? "Approved then quarantined" : comment);
                po = pos.findById(id).orElseThrow();
            }
            PurchaseOrderLine line = po.getLines().stream()
                    .filter(l -> l.getReceivedQty().compareTo(l.getQty()) < 0)
                    .findFirst()
                    .orElse(po.getLines().isEmpty() ? null : po.getLines().get(0));
            if (line == null) {
                throw new IllegalArgumentException("No lines to receive into quarantine.");
            }
            BigDecimal remaining = line.getQty().subtract(line.getReceivedQty());
            if (remaining.signum() <= 0) {
                throw new IllegalArgumentException("This PO is already fully received. Use quality inspection to hold the lot.");
            }
            return receive(id, line.getProduct().getId(), remaining, actor);
        }
        return movePo(id, PurchaseStatus.APPROVED, actor, comment == null ? "Approved" : comment);
    }

    public PurchaseOrder requirePo(String poNoOrId) {
        if (poNoOrId == null || poNoOrId.isBlank()) {
            throw new IllegalArgumentException("Name the purchase order.");
        }
        String key = poNoOrId.trim();
        if (key.matches("\\d+")) {
            return pos.findById(Long.valueOf(key)).orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        }
        return pos.findByPoNo(key.toUpperCase(Locale.ROOT)).or(() -> pos.findByPoNo(key))
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + key));
    }

    @Transactional
    public Map<String, Object> movePo(Long id, PurchaseStatus target, String actor, String comment) {
        PurchaseOrder po = pos.findById(id).orElseThrow();
        PurchaseStatus from = po.getStatus();
        po.setStatus(target);
        pos.save(po);
        workflow.step(WorkflowEntity.PURCHASE_ORDER, po.getPoNo(), from.name(), target.name(), actor, comment);
        audit.record(actor, "PO_" + target, "PURCHASE_ORDER", po.getPoNo(), comment);
        if (target == PurchaseStatus.APPROVED) {
            events.publish(DomainEvent.of("rc.orders", "PO_APPROVED", po.getPoNo(), Map.of("supplier", po.getSupplier().getName())));
        }
        return view(po);
    }

    @Transactional
    public Map<String, Object> receive(Long poId, Long productId, BigDecimal qty, String actor) {
        PurchaseOrder po = pos.findById(poId).orElseThrow();
        PurchaseOrderLine line = po.getLines().stream()
                .filter(l -> l.getProduct().getId().equals(productId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Product not on PO"));
        line.setReceivedQty(line.getReceivedQty().add(qty));
        InventoryLot lot = InventoryLot.builder()
                .lotCode("LOT-" + po.getPoNo() + "-" + productId + "-" + Instant.now().toEpochMilli())
                .product(line.getProduct())
                .warehouse(po.getWarehouse())
                .supplier(po.getSupplier())
                .receivedQty(qty)
                .availableQty(BigDecimal.ZERO)
                .reservedQty(BigDecimal.ZERO)
                .receivedOn(LocalDate.now())
                .expiryOn(LocalDate.now().plusDays(line.getProduct().getShelfLifeDays() == null ? 180 : line.getProduct().getShelfLifeDays()))
                .grade(line.getProduct().getGrade())
                .status(LotStatus.QUARANTINE)
                .build();
        lots.save(lot);
        boolean complete = po.getLines().stream().allMatch(l -> l.getReceivedQty().compareTo(l.getQty()) >= 0);
        po.setStatus(complete ? PurchaseStatus.RECEIVED : PurchaseStatus.PARTIALLY_RECEIVED);
        pos.save(po);
        QualityInspection qi = inspections.save(QualityInspection.builder()
                .inspectionNo("QC-" + Instant.now().toEpochMilli())
                .lot(lot)
                .purchaseOrder(po)
                .result(InspectionResult.PENDING)
                .build());
        events.publish(DomainEvent.of("rc.inventory", "LOT_QUARANTINE", lot.getLotCode(),
                Map.of("po", po.getPoNo(), "inspection", qi.getInspectionNo())));
        audit.record(actor, "GRN", "LOT", lot.getLotCode(), "Received " + qty + " into quarantine");
        workflow.step(WorkflowEntity.QUALITY_INSPECTION, qi.getInspectionNo(), null, "PENDING", actor, "GRN created QC");
        return Map.of("lot", lot.getLotCode(), "inspection", qi.getInspectionNo(), "poStatus", po.getStatus());
    }

    @Transactional
    public Map<String, Object> inspect(Long inspectionId, InspectionResult result, BigDecimal moisture, String appearance,
                                      String foreignMatter, String remarks, String actor) {
        QualityInspection qi = inspections.findById(inspectionId).orElseThrow();
        String from = qi.getResult() == null ? "PENDING" : qi.getResult().name();
        qi.setResult(result);
        qi.setMoisturePct(moisture);
        qi.setAppearance(appearance);
        qi.setForeignMatter(foreignMatter);
        qi.setRemarks(remarks);
        qi.setInspector(actor);
        qi.setInspectedAt(Instant.now());
        InventoryLot lot = qi.getLot();
        lot.setMoisturePct(moisture);
        if (result == InspectionResult.PASSED) {
            lot.setStatus(LotStatus.AVAILABLE);
            if (lot.getAvailableQty() == null || lot.getAvailableQty().signum() == 0) {
                lot.setAvailableQty(lot.getReceivedQty());
            }
            events.publish(DomainEvent.of("rc.inventory", "QC_PASSED", lot.getLotCode(), Map.of()));
        } else if (result == InspectionResult.HOLD) {
            lot.setStatus(LotStatus.HOLD);
        } else if (result == InspectionResult.REJECTED) {
            lot.setStatus(LotStatus.REJECTED);
            events.publish(DomainEvent.of("rc.inventory", "QC_REJECTED", lot.getLotCode(), Map.of()));
        }
        lots.save(lot);
        inspections.save(qi);
        workflow.step(WorkflowEntity.QUALITY_INSPECTION, qi.getInspectionNo(), from, result.name(), actor, remarks);
        audit.record(actor, "QC_" + result, "LOT", lot.getLotCode(), remarks);
        return viewInspection(qi);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPos(String username, boolean supplierOnly) {
        List<PurchaseOrder> list = supplierOnly
                ? pos.findBySupplier_PortalUser_Username(username)
                : pos.findAll();
        return list.stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listInspections(String username, boolean supplierOnly) {
        return inspections.findAll().stream()
                .filter(qi -> !supplierOnly || qi.getLot().getSupplier() != null
                        && qi.getLot().getSupplier().getPortalUser() != null
                        && username.equals(qi.getLot().getSupplier().getPortalUser().getUsername()))
                .map(this::viewInspection)
                .toList();
    }

    public Map<String, Object> view(PurchaseOrder po) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", po.getId());
        m.put("poNo", po.getPoNo());
        m.put("supplier", po.getSupplier().getName());
        m.put("supplierContact", po.getSupplier().getContactName());
        m.put("supplierEmail", po.getSupplier().getEmail());
        m.put("supplierPhone", po.getSupplier().getPhone());
        m.put("warehouse", po.getWarehouse() == null ? "" : po.getWarehouse().getName());
        m.put("warehouseCity", po.getWarehouse() == null ? "" : po.getWarehouse().getCity());
        m.put("status", po.getStatus());
        m.put("total", po.getTotal());
        m.put("expectedDate", po.getExpectedDate());
        m.put("notes", po.getNotes());
        m.put("createdAt", po.getCreatedAt());
        m.put("createdBy", po.getCreatedBy());
        m.put("lines", po.getLines().stream().map(l -> {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("productId", l.getProduct().getId());
            line.put("sku", l.getProduct().getSku());
            line.put("name", l.getProduct().getName());
            line.put("uom", l.getProduct().getUom());
            line.put("qty", l.getQty());
            line.put("receivedQty", l.getReceivedQty());
            line.put("unitCost", l.getUnitCost());
            line.put("lineTotal", l.getQty() == null || l.getUnitCost() == null
                    ? BigDecimal.ZERO : l.getQty().multiply(l.getUnitCost()));
            return line;
        }).toList());
        return m;
    }

    public Map<String, Object> viewInspection(QualityInspection qi) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", qi.getId());
        m.put("inspectionNo", qi.getInspectionNo());
        m.put("lotCode", qi.getLot().getLotCode());
        m.put("product", qi.getLot().getProduct().getName());
        m.put("result", qi.getResult());
        m.put("moisturePct", qi.getMoisturePct());
        m.put("appearance", qi.getAppearance());
        m.put("foreignMatter", qi.getForeignMatter());
        m.put("remarks", qi.getRemarks());
        m.put("inspector", qi.getInspector());
        m.put("poNo", qi.getPurchaseOrder() == null ? "" : qi.getPurchaseOrder().getPoNo());
        return m;
    }
}
