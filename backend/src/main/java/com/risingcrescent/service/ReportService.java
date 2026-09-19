package com.risingcrescent.service;

import com.risingcrescent.domain.entity.Invoice;
import com.risingcrescent.domain.entity.PurchaseOrder;
import com.risingcrescent.domain.entity.QualityInspection;
import com.risingcrescent.domain.entity.SalesOrder;
import com.risingcrescent.domain.enums.OrderStatus;
import com.risingcrescent.repo.InvoiceRepository;
import com.risingcrescent.repo.PurchaseOrderRepository;
import com.risingcrescent.repo.QualityInspectionRepository;
import com.risingcrescent.repo.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Dubai");

    private final SalesOrderRepository orders;
    private final InvoiceRepository invoices;
    private final PurchaseOrderRepository pos;
    private final QualityInspectionRepository inspections;

    @Transactional(readOnly = true)
    public Map<String, Object> daily(LocalDate day) {
        LocalDate d = day == null ? LocalDate.now(ZONE) : day;
        Instant from = d.atStartOfDay(ZONE).toInstant();
        Instant to = d.plusDays(1).atStartOfDay(ZONE).toInstant();
        return period("DAILY", d.toString(), from, to);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> monthly(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        Instant from = ym.atDay(1).atStartOfDay(ZONE).toInstant();
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        return period("MONTHLY", ym.toString(), from, to);
    }

    private Map<String, Object> period(String kind, String label, Instant from, Instant to) {
        List<SalesOrder> sales = orders.findAll().stream()
                .filter(o -> inRange(o.getPlacedAt(), from, to))
                .toList();
        List<Invoice> invs = invoices.findAll().stream()
                .filter(i -> inRange(i.getIssuedAt(), from, to))
                .toList();
        List<PurchaseOrder> purchase = pos.findAll().stream()
                .filter(p -> inRange(p.getCreatedAt(), from, to))
                .toList();
        List<QualityInspection> qc = inspections.findAll().stream()
                .filter(q -> inRange(q.getInspectedAt(), from, to))
                .toList();

        BigDecimal salesTotal = sales.stream().map(this::orderTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invoiceTotal = invs.stream().map(this::invoiceAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invoicePaid = invs.stream().map(this::invoicePaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal poTotal = purchase.stream().map(this::poTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        long cancelled = sales.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
        long delivered = sales.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("label", label);
        m.put("timezone", ZONE.getId());
        m.put("orderCount", sales.size());
        m.put("cancelledCount", cancelled);
        m.put("deliveredCount", delivered);
        m.put("salesTotal", salesTotal);
        m.put("invoiceCount", invs.size());
        m.put("invoiceTotal", invoiceTotal);
        m.put("invoicePaid", invoicePaid);
        m.put("poCount", purchase.size());
        m.put("poTotal", poTotal);
        m.put("qcCount", qc.size());
        m.put("orders", sales.stream().map(o -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderNo", o.getOrderNo());
            row.put("customer", o.getCustomer() == null ? "" : o.getCustomer().getFullName());
            row.put("status", o.getStatus());
            row.put("paymentStatus", o.getPaymentStatus());
            row.put("total", o.getTotal());
            row.put("placedAt", o.getPlacedAt());
            return row;
        }).toList());
        m.put("invoices", invs.stream().map(i -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("invoiceNo", i.getInvoiceNo());
            row.put("orderNo", i.getOrder() == null ? "" : i.getOrder().getOrderNo());
            row.put("customer", i.getOrder() == null || i.getOrder().getCustomer() == null
                    ? "" : i.getOrder().getCustomer().getFullName());
            row.put("amount", i.getAmount());
            row.put("paidAmount", i.getPaidAmount());
            row.put("status", i.getStatus());
            row.put("issuedAt", i.getIssuedAt());
            return row;
        }).toList());
        m.put("purchaseOrders", purchase.stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("poNo", p.getPoNo());
            row.put("supplier", p.getSupplier() == null ? "" : p.getSupplier().getName());
            row.put("warehouse", p.getWarehouse() == null ? "" : p.getWarehouse().getName());
            row.put("status", p.getStatus());
            row.put("total", p.getTotal());
            row.put("createdAt", p.getCreatedAt());
            row.put("lines", p.getLines() == null ? 0 : p.getLines().size());
            return row;
        }).toList());
        m.put("quality", qc.stream().map(q -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("inspectionNo", q.getInspectionNo());
            row.put("lotCode", q.getLot() == null ? "" : q.getLot().getLotCode());
            row.put("product", q.getLot() == null || q.getLot().getProduct() == null
                    ? "" : q.getLot().getProduct().getName());
            row.put("result", q.getResult());
            row.put("inspector", q.getInspector() == null ? "" : q.getInspector());
            row.put("inspectedAt", q.getInspectedAt());
            return row;
        }).toList());
        return m;
    }

    private boolean inRange(Instant t, Instant from, Instant to) {
        return t != null && !t.isBefore(from) && t.isBefore(to);
    }

    private BigDecimal orderTotal(SalesOrder o) {
        return o.getTotal() == null ? BigDecimal.ZERO : o.getTotal();
    }

    private BigDecimal invoiceAmount(Invoice i) {
        return i.getAmount() == null ? BigDecimal.ZERO : i.getAmount();
    }

    private BigDecimal invoicePaid(Invoice i) {
        return i.getPaidAmount() == null ? BigDecimal.ZERO : i.getPaidAmount();
    }

    private BigDecimal poTotal(PurchaseOrder p) {
        return p.getTotal() == null ? BigDecimal.ZERO : p.getTotal();
    }
}
