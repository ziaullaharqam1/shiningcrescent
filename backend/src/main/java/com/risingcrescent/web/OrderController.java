package com.risingcrescent.web;

import com.risingcrescent.domain.enums.OrderStatus;
import com.risingcrescent.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orders;

    public record CheckoutRequest(String shipToName, String shipToPhone, String shipToAddress, String notes,
                                 Double dropLat, Double dropLng, String paymentMethod, Boolean leaveAtDoor) {}

    public record MoveRequest(OrderStatus status, String comment) {}

    public record ConfirmPayRequest(String paymentIntentId) {}

    @PostMapping("/checkout")
    public Map<String, Object> checkout(Principal p, @RequestBody CheckoutRequest req) {
        return orders.checkout(p.getName(), req.shipToName(), req.shipToPhone(), req.shipToAddress(), req.notes(),
                req.dropLat(), req.dropLng(), req.paymentMethod(), req.leaveAtDoor());
    }

    @PostMapping("/{id}/stripe/confirm")
    public Map<String, Object> confirmPay(@PathVariable Long id, @RequestBody(required = false) ConfirmPayRequest req, Principal p) {
        return orders.confirmStripe(id, req == null ? null : req.paymentIntentId(), p.getName());
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication auth) {
        boolean staff = hasStaff(auth);
        return orders.list(auth.getName(), staff);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id, Authentication auth) {
        return orders.get(id, auth.getName(), hasStaff(auth));
    }

    @PostMapping("/{id}/transition")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('SALES_ORDERS:UPDATE','SALES_ORDERS:APPROVE','FULFILLMENT:UPDATE')")
    public Map<String, Object> move(@PathVariable Long id, @RequestBody MoveRequest req, Principal p) {
        return orders.transition(id, req.status(), p.getName(), req.comment());
    }

    private boolean hasStaff(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> {
            String v = a.getAuthority();
            return v.startsWith("ROLE_") && !v.equals("ROLE_CUSTOMER") && !v.equals("ROLE_SUPPLIER");
        }) || auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SALES_ORDERS:VIEW"));
    }
}
