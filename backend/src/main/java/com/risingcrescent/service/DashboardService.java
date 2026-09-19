package com.risingcrescent.service;

import com.risingcrescent.domain.enums.InspectionResult;
import com.risingcrescent.domain.enums.OrderStatus;
import com.risingcrescent.domain.enums.PurchaseStatus;
import com.risingcrescent.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final SalesOrderRepository orders;
    private final PurchaseOrderRepository pos;
    private final InventoryLotRepository lots;
    private final QualityInspectionRepository inspections;
    private final ProductRepository products;

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ordersPlaced", orders.countByStatus(OrderStatus.PLACED));
        m.put("ordersPicking", orders.countByStatus(OrderStatus.PICKING));
        m.put("ordersShipped", orders.countByStatus(OrderStatus.SHIPPED));
        m.put("openPos", pos.findByStatus(PurchaseStatus.APPROVED).size()
                + pos.findByStatus(PurchaseStatus.SENT_TO_SUPPLIER).size()
                + pos.findByStatus(PurchaseStatus.ACKNOWLEDGED).size());
        m.put("pendingQc", inspections.findByResult(InspectionResult.PENDING).size());
        m.put("availableLots", lots.findAll().stream().filter(l -> l.getStatus().name().equals("AVAILABLE")).count());
        m.put("catalogSkus", products.count());
        BigDecimal onHand = lots.findAll().stream()
                .map(l -> l.getAvailableQty() == null ? BigDecimal.ZERO : l.getAvailableQty())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("onHandQty", onHand);
        return m;
    }

    public Map<String, Object> supplierSnapshot(String username) {
        var assigned = pos.findBySupplier_PortalUser_Username(username);
        var lotsFor = lots.findBySupplier_PortalUser_Username(username);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("supplierDesk", true);
        m.put("assignedPos", assigned.size());
        m.put("awaitingAck", assigned.stream().filter(p -> p.getStatus() == PurchaseStatus.SENT_TO_SUPPLIER).count());
        m.put("openPos", assigned.stream().filter(p -> p.getStatus() == PurchaseStatus.APPROVED
                || p.getStatus() == PurchaseStatus.SENT_TO_SUPPLIER
                || p.getStatus() == PurchaseStatus.ACKNOWLEDGED).count());
        m.put("availableLots", lotsFor.stream().filter(l -> "AVAILABLE".equals(l.getStatus().name())).count());
        m.put("quarantineLots", lotsFor.stream().filter(l -> "QUARANTINE".equals(l.getStatus().name())).count());
        BigDecimal onHand = lotsFor.stream()
                .map(l -> l.getAvailableQty() == null ? BigDecimal.ZERO : l.getAvailableQty())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("onHandQty", onHand);
        m.put("ordersPlaced", 0);
        m.put("ordersPicking", 0);
        m.put("ordersShipped", 0);
        m.put("pendingQc", lotsFor.stream().filter(l -> "QUARANTINE".equals(l.getStatus().name())).count());
        m.put("catalogSkus", 0);
        return m;
    }
}
