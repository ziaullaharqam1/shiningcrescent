package com.shiningcrescent.service;

import com.shiningcrescent.audit.AuditService;
import com.shiningcrescent.domain.entity.Invoice;
import com.shiningcrescent.domain.entity.Payment;
import com.shiningcrescent.domain.enums.InvoiceStatus;
import com.shiningcrescent.repo.InvoiceRepository;
import com.shiningcrescent.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FinanceService {
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listInvoices() {
        return invoices.findAll().stream().map(this::view).toList();
    }

    @Transactional
    public Map<String, Object> pay(Long invoiceId, BigDecimal amount, String method, String reference, String actor) {
        Invoice inv = invoices.findById(invoiceId).orElseThrow();
        payments.save(Payment.builder()
                .invoice(inv)
                .amount(amount)
                .method(method)
                .reference(reference)
                .paidAt(Instant.now())
                .recordedBy(actor)
                .build());
        inv.setPaidAmount(inv.getPaidAmount().add(amount));
        if (inv.getPaidAmount().compareTo(inv.getAmount()) >= 0) {
            inv.setStatus(InvoiceStatus.PAID);
        } else {
            inv.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        invoices.save(inv);
        audit.record(actor, "PAYMENT", "INVOICE", inv.getInvoiceNo(), amount.toPlainString());
        return view(inv);
    }

    private Map<String, Object> view(Invoice inv) {
        var order = inv.getOrder();
        var customer = order.getCustomer();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inv.getId());
        m.put("invoiceNo", inv.getInvoiceNo());
        m.put("orderNo", order.getOrderNo());
        m.put("customer", customer.getFullName());
        m.put("customerEmail", publicEmail(customer.getEmail()));
        m.put("customerPhone", customer.getPhone());
        m.put("companyName", customer.getCompanyName());
        m.put("shipToName", order.getShipToName());
        m.put("shipToPhone", order.getShipToPhone());
        m.put("shipToAddress", order.getShipToAddress());
        m.put("subtotal", order.getSubtotal());
        m.put("tax", order.getTax());
        m.put("amount", inv.getAmount());
        m.put("paidAmount", inv.getPaidAmount());
        m.put("status", inv.getStatus());
        m.put("dueDate", inv.getDueDate());
        m.put("issuedAt", inv.getIssuedAt());
        m.put("lines", order.getLines().stream().map(l -> {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("sku", l.getProduct().getSku());
            line.put("name", l.getProduct().getName());
            line.put("uom", l.getProduct().getUom());
            line.put("qty", l.getQty());
            line.put("unitPrice", l.getUnitPrice());
            line.put("lineTotal", l.getLineTotal());
            return line;
        }).toList());
        return m;
    }

    private String publicEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        if (email.toLowerCase().endsWith("@phone.shiningcrescent.local")
                || email.toLowerCase().endsWith("@phone.risingcrescent.local")) {
            return "";
        }
        return email;
    }
}
