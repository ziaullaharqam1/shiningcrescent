package com.risingcrescent.service;

import com.risingcrescent.audit.AuditService;
import com.risingcrescent.config.DeliveryProperties;
import com.risingcrescent.domain.entity.*;
import com.risingcrescent.domain.enums.*;
import com.risingcrescent.event.DomainEvent;
import com.risingcrescent.event.EventPublisher;
import com.risingcrescent.repo.*;
import com.risingcrescent.workflow.WorkflowService;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final SalesOrderRepository orders;
    private final UserAccountRepository users;
    private final CartService cartService;
    private final InventoryService inventory;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final EventPublisher events;
    private final AuditService audit;
    private final WorkflowService workflow;
    private final StripeGateway stripe;
    private final DeliveryService delivery;
    private final DeliveryProperties deliveryProps;
    private final PlatformTransactionManager transactionManager;

    public Map<String, Object> checkout(String username, String shipToName, String shipToPhone, String shipToAddress,
                                        String notes, Double dropLat, Double dropLng, String paymentMethod,
                                        Boolean leaveAtDoor) {
        Map<String, Object> view = inTx(() -> createCheckout(username, shipToName, shipToPhone, shipToAddress,
                notes, dropLat, dropLng, paymentMethod, leaveAtDoor));
        if (!Boolean.TRUE.equals(view.get("awaitingCard"))) {
            afterPayment(((Number) view.get("id")).longValue(), username);
            Map<String, Object> done = get(((Number) view.get("id")).longValue(), username, true);
            done.put("awaitingCard", false);
            done.put("paymentMethod", "CASH_ON_DELIVERY");
            return done;
        }
        return view;
    }

    private Map<String, Object> createCheckout(String username, String shipToName, String shipToPhone, String shipToAddress,
                                               String notes, Double dropLat, Double dropLng, String paymentMethod,
                                               Boolean leaveAtDoor) {
        UserAccount customer = users.findByUsername(username).orElseThrow();
        Cart cart = cartService.getOrCreate(username);
        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }
        Map<String, Object> snap = cartService.snapshot(cart, customer);
        boolean cash = paymentMethod != null && (paymentMethod.equalsIgnoreCase("COD")
                || paymentMethod.equalsIgnoreCase("CASH_ON_DELIVERY")
                || paymentMethod.equalsIgnoreCase("CASH"));
        Channel channel = (Channel) snap.get("channel");
        double[] geo = GeoUtil.pointForAddress(shipToAddress, deliveryProps.warehouseLat(), deliveryProps.warehouseLng());
        SalesOrder order = SalesOrder.builder()
                .orderNo(nextNo("SO"))
                .customer(customer)
                .channel(channel)
                .status(OrderStatus.AWAITING_PAYMENT)
                .paymentStatus(cash ? PaymentStatus.UNPAID : PaymentStatus.PENDING)
                .paymentMethod(cash ? "CASH_ON_DELIVERY" : "CARD")
                .shipToName(shipToName)
                .shipToPhone((shipToPhone == null || shipToPhone.isBlank()) ? customer.getPhone() : shipToPhone)
                .shipToAddress(shipToAddress)
                .notes(notes)
                .leaveAtDoor(Boolean.TRUE.equals(leaveAtDoor))
                .dropLat(dropLat != null ? dropLat : geo[0])
                .dropLng(dropLng != null ? dropLng : geo[1])
                .subtotal((BigDecimal) snap.get("subtotal"))
                .tax((BigDecimal) snap.get("tax"))
                .total((BigDecimal) snap.get("total"))
                .lastActor(username)
                .build();
        for (CartItem item : cart.getItems()) {
            BigDecimal price = channel == Channel.WHOLESALE ? item.getProduct().getWholesalePrice() : item.getProduct().getRetailPrice();
            order.getLines().add(SalesOrderLine.builder()
                    .order(order)
                    .product(item.getProduct())
                    .qty(item.getQty())
                    .unitPrice(price)
                    .lineTotal(price.multiply(item.getQty()))
                    .build());
        }
        orders.save(order);
        workflow.step(WorkflowEntity.SALES_ORDER, order.getOrderNo(), null, OrderStatus.AWAITING_PAYMENT.name(), username,
                cash ? "Checkout, cash on delivery" : "Checkout, awaiting card payment");
        audit.record(username, "ORDER_CHECKOUT", "SALES_ORDER", order.getOrderNo(), "total=" + order.getTotal());

        if (cash) {
            cartService.clear(cart);
            transition(order, OrderStatus.PLACED, username, "Cash on delivery");
            Map<String, Object> done = view(order, username, true);
            done.put("awaitingCard", false);
            done.put("paymentMethod", "CASH_ON_DELIVERY");
            return done;
        }

        Map<String, Object> card = view(order, username, true);
        card.put("awaitingCard", true);
        attachStripe(order, card);
        orders.save(order);
        return card;
    }

    private void attachStripe(SalesOrder order, Map<String, Object> view) {
        Map<String, Object> cfg = stripe.config();
        view.put("stripeMock", cfg.get("mock"));
        view.put("stripePublishableKey", cfg.get("publishableKey"));
        view.put("stripeCurrency", cfg.get("currency"));
        if (stripe.mockMode()) {
            if (order.getStripePaymentIntentId() == null) {
                order.setStripePaymentIntentId("pi_mock_" + order.getOrderNo());
            }
            view.put("clientSecret", "mock_secret_" + order.getOrderNo());
            view.put("paymentIntentId", order.getStripePaymentIntentId());
            return;
        }
        PaymentIntent pi = stripe.createPaymentIntent(order.getOrderNo(), order.getTotal(), "order");
        order.setStripePaymentIntentId(pi.getId());
        view.put("clientSecret", pi.getClientSecret());
        view.put("paymentIntentId", pi.getId());
    }

    public Map<String, Object> confirmStripe(Long orderId, String paymentIntentId, String actor) {
        String intentId = paymentIntentId;
        Long paidId = inTx(() -> {
            SalesOrder order = orders.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
            String pid = intentId;
            if (!stripe.mockMode()) {
                PaymentIntent pi = stripe.retrieve(pid == null ? order.getStripePaymentIntentId() : pid);
                if (!"succeeded".equals(pi.getStatus())) {
                    throw new IllegalStateException("Stripe payment is not complete yet");
                }
                pid = pi.getId();
            } else if (pid == null) {
                pid = order.getStripePaymentIntentId();
            }
            recordCardPayment(order, pid, actor);
            return order.getId();
        });
        afterPayment(paidId, actor);
        return orderSnapshot(paidId, actor);
    }

    public Map<String, Object> markPaidByIntent(String paymentIntentId) {
        return settleExisting(inTx(() -> {
            SalesOrder order = orders.findByStripePaymentIntentId(paymentIntentId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found for payment"));
            recordCardPayment(order, paymentIntentId, "stripe");
            return order.getId();
        }), "stripe");
    }

    public Map<String, Object> markPaidByOrderNo(String orderNo, String paymentIntentId) {
        return settleExisting(inTx(() -> {
            SalesOrder order = orders.findByOrderNo(orderNo).orElseThrow(() -> new IllegalArgumentException("Order not found"));
            recordCardPayment(order, paymentIntentId, "stripe");
            return order.getId();
        }), "stripe");
    }

    private Map<String, Object> settleExisting(Long orderId, String actor) {
        afterPayment(orderId, actor);
        return orderSnapshot(orderId, actor);
    }

    private Map<String, Object> orderSnapshot(Long orderId, String actor) {
        return inTx(() -> {
            SalesOrder order = orders.findWithLinesById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found"));
            return get(order.getId(), order.getCustomer().getUsername(), true);
        });
    }

    private void recordCardPayment(SalesOrder order, String paymentIntentId, String actor) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return;
        }
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            order.setPaymentStatus(PaymentStatus.PAID);
            if (paymentIntentId != null) {
                order.setStripePaymentIntentId(paymentIntentId);
            }
            orders.save(order);
            return;
        }
        if (!payments.existsByReference(paymentIntentId)) {
            payments.save(Payment.builder()
                    .order(order)
                    .amount(order.getTotal())
                    .method("STRIPE")
                    .reference(paymentIntentId)
                    .paidAt(Instant.now())
                    .recordedBy(actor)
                    .build());
        }
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStripePaymentIntentId(paymentIntentId);
        orders.save(order);
        cartService.clear(cartService.getOrCreate(order.getCustomer().getUsername()));
        transition(order, OrderStatus.PLACED, actor, "Paid with card");
        events.publish(DomainEvent.of("rc.orders", "ORDER_PLACED", order.getOrderNo(),
                Map.of("customer", order.getCustomer().getUsername(), "total", order.getTotal().toPlainString())));
    }

    private void afterPayment(Long orderId, String actor) {
        try {
            inNewTx(() -> {
                SalesOrder order = orders.findWithLinesById(orderId).orElseThrow();
                if (order.getStatus() != OrderStatus.PLACED) {
                    return;
                }
                UserAccount customer = order.getCustomer();
                if (customer.getPreferredChannel() == Channel.WHOLESALE
                        && customer.getCreditLimit().signum() > 0
                        && customer.getOutstandingCredit().add(order.getTotal()).compareTo(customer.getCreditLimit()) > 0
                        && order.getPaymentStatus() != PaymentStatus.PAID) {
                    transition(order, OrderStatus.CREDIT_HOLD, actor, "Credit limit exceeded");
                    events.publish(DomainEvent.of("rc.orders", "ORDER_CREDIT_HOLD", order.getOrderNo(),
                            Map.of("customer", customer.getUsername())));
                    return;
                }
                fulfillPaid(order, actor);
            });
        } catch (RuntimeException ex) {
            log.warn("Fulfill after payment for order {}: {}", orderId, ex.getMessage());
            try {
                inNewTx(() -> {
                    SalesOrder latest = orders.findById(orderId).orElseThrow();
                    if (latest.getStatus() == OrderStatus.PLACED) {
                        transition(latest, OrderStatus.CONFIRMED, actor, "Paid — warehouse will pick when stock is ready");
                    }
                });
            } catch (RuntimeException ignored) {
                log.warn("Could not mark order {} confirmed after fulfill failure", orderId);
            }
        }
        try {
            inNewTx(() -> delivery.dispatch(orders.findWithLinesById(orderId).orElseThrow()));
        } catch (RuntimeException ex) {
            log.warn("Dispatch after payment for order {}: {}", orderId, ex.getMessage());
            try {
                SalesOrder order = orders.findById(orderId).orElse(null);
                if (order != null) {
                    audit.record(actor, "NOON_DISPATCH_WAIT", "SALES_ORDER", order.getOrderNo(),
                            ex.getMessage() == null ? "Rider not assigned yet" : ex.getMessage());
                }
            } catch (RuntimeException ignored) {
                /* keep payment successful */
            }
        }
    }

    private <T> T inTx(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private void inNewTx(Runnable action) {
        TransactionTemplate t = new TransactionTemplate(transactionManager);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        t.executeWithoutResult(status -> action.run());
    }

    private void fulfillPaid(SalesOrder order, String actor) {
        transition(order, OrderStatus.CONFIRMED, actor, "Paid — confirmed");
        transition(order, OrderStatus.PICKING, actor, "Noon dispatch — FEFO pick");
        transition(order, OrderStatus.PACKED, actor, "Packed for Noon rider");
        transition(order, OrderStatus.SHIPPED, actor, "Handed to Noon");
    }

    @Transactional
    public Map<String, Object> transition(Long id, OrderStatus target, String actor, String comment) {
        SalesOrder order = orders.findWithLinesById(id).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        Map<String, Object> result = transition(order, target, actor, comment);
        if (target == OrderStatus.SHIPPED) {
            delivery.dispatch(order);
        }
        return result;
    }

    private Map<String, Object> transition(SalesOrder order, OrderStatus target, String actor, String comment) {
        OrderStatus from = order.getStatus();
        validateMove(from, target);
        if (target == OrderStatus.PICKING) {
            inventory.allocate(order);
        }
        if (target == OrderStatus.CANCELLED && from == OrderStatus.PICKING) {
            inventory.release(order);
        }
        if (target == OrderStatus.PLACED || target == OrderStatus.DELIVERED) {
            ensureInvoice(order);
        }
        order.setStatus(target);
        order.setLastActor(actor);
        orders.save(order);
        workflow.step(WorkflowEntity.SALES_ORDER, order.getOrderNo(), from.name(), target.name(), actor, comment);
        audit.record(actor, "ORDER_" + target, "SALES_ORDER", order.getOrderNo(), comment);
        if (target == OrderStatus.CONFIRMED) {
            events.publish(DomainEvent.of("rc.orders", "ORDER_CONFIRMED", order.getOrderNo(),
                    Map.of("customer", order.getCustomer().getUsername())));
        }
        if (target == OrderStatus.SHIPPED) {
            events.publish(DomainEvent.of("rc.orders", "ORDER_SHIPPED", order.getOrderNo(),
                    Map.of("customer", order.getCustomer().getUsername())));
        }
        if (target == OrderStatus.DELIVERED) {
            events.publish(DomainEvent.of("rc.orders", "ORDER_DELIVERED", order.getOrderNo(),
                    Map.of("customer", order.getCustomer().getUsername())));
        }
        return view(order, actor, true);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String username, boolean all) {
        List<SalesOrder> list = all ? orders.findAll() : orders.findByCustomerOrderByPlacedAtDesc(
                users.findByUsername(username).orElseThrow());
        return list.stream().sorted(Comparator.comparing(SalesOrder::getPlacedAt).reversed())
                .map(o -> view(o, username, false)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id, String username, boolean staff) {
        SalesOrder order = orders.findWithLinesById(id).orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!staff && !order.getCustomer().getUsername().equals(username)) {
            throw new IllegalArgumentException("Order not found");
        }
        Map<String, Object> m = view(order, username, true);
        m.put("workflow", workflow.trail(WorkflowEntity.SALES_ORDER, order.getOrderNo()));
        Map<String, Object> track = delivery.tracking(id, username, true);
        track.remove("orderId");
        track.remove("orderNo");
        track.remove("orderStatus");
        track.remove("paymentStatus");
        if (track.containsKey("status")) {
            track.put("deliveryStatus", track.remove("status"));
        }
        if (track.containsKey("assigned")) {
            track.put("deliveryAssigned", track.remove("assigned"));
        }
        m.putAll(track);
        return m;
    }

    private Map<String, Object> view(SalesOrder o, String username, boolean withLines) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("status", o.getStatus());
        m.put("paymentStatus", o.getPaymentStatus());
        m.put("paymentMethod", o.getPaymentMethod());
        m.put("channel", o.getChannel());
        m.put("customer", o.getCustomer().getFullName());
        m.put("customerUsername", o.getCustomer().getUsername());
        String email = o.getCustomer().getEmail();
        if (email != null && !email.toLowerCase().endsWith("@phone.risingcrescent.local")) {
            m.put("customerEmail", email);
        }
        m.put("total", o.getTotal());
        m.put("subtotal", o.getSubtotal());
        m.put("tax", o.getTax());
        m.put("placedAt", o.getPlacedAt());
        m.put("shipToAddress", o.getShipToAddress());
        m.put("shipToName", o.getShipToName());
        m.put("shipToPhone", o.getShipToPhone());
        m.put("notes", o.getNotes());
        m.put("leaveAtDoor", o.isLeaveAtDoor());
        m.put("dropLat", o.getDropLat());
        m.put("dropLng", o.getDropLng());
        if (withLines) {
            m.put("lines", o.getLines().stream().map(l -> Map.of(
                    "sku", l.getProduct().getSku(),
                    "name", l.getProduct().getName(),
                    "qty", l.getQty(),
                    "unitPrice", l.getUnitPrice(),
                    "lineTotal", l.getLineTotal(),
                    "allocatedLot", l.getAllocatedLot() == null ? "" : l.getAllocatedLot()
            )).toList());
        }
        return m;
    }

    private void validateMove(OrderStatus from, OrderStatus to) {
        Map<OrderStatus, Set<OrderStatus>> allowed = new EnumMap<>(OrderStatus.class);
        allowed.put(OrderStatus.AWAITING_PAYMENT, Set.of(OrderStatus.PLACED, OrderStatus.CANCELLED));
        allowed.put(OrderStatus.PLACED, Set.of(OrderStatus.CONFIRMED, OrderStatus.CREDIT_HOLD, OrderStatus.CANCELLED));
        allowed.put(OrderStatus.CREDIT_HOLD, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        allowed.put(OrderStatus.CONFIRMED, Set.of(OrderStatus.PICKING, OrderStatus.CANCELLED));
        allowed.put(OrderStatus.PICKING, Set.of(OrderStatus.PACKED, OrderStatus.CANCELLED));
        allowed.put(OrderStatus.PACKED, Set.of(OrderStatus.SHIPPED));
        allowed.put(OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED));
        allowed.put(OrderStatus.DELIVERED, Set.of(OrderStatus.RETURN_REQUESTED));
        allowed.put(OrderStatus.RETURN_REQUESTED, Set.of(OrderStatus.RETURNED));
        if (!allowed.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("This order cannot be moved to that status.");
        }
    }

    private void ensureInvoice(SalesOrder order) {
        Invoice inv = invoices.findByOrder_Id(order.getId()).orElseGet(() -> invoices.save(Invoice.builder()
                .invoiceNo(nextNo("INV"))
                .order(order)
                .status(InvoiceStatus.ISSUED)
                .amount(order.getTotal())
                .paidAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().plusDays(7))
                .issuedAt(Instant.now())
                .build()));
        if (order.getPaymentStatus() == PaymentStatus.PAID && inv.getPaidAmount().signum() == 0) {
            inv.setPaidAmount(order.getTotal());
            inv.setStatus(InvoiceStatus.PAID);
            invoices.save(inv);
            for (Payment p : payments.findByOrder_Id(order.getId())) {
                if (p.getInvoice() == null) {
                    p.setInvoice(inv);
                    payments.save(p);
                }
            }
        }
    }

    private String nextNo(String prefix) {
        return prefix + "-" + Instant.now().toEpochMilli();
    }
}
