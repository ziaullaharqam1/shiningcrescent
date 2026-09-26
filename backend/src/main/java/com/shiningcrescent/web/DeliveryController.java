package com.shiningcrescent.web;

import com.shiningcrescent.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/orders/{id}/delivery")
@RequiredArgsConstructor
public class DeliveryController {
    private final DeliveryService delivery;

    public record TipRequest(BigDecimal amount) {}
    public record FeedbackRequest(Integer rating, String comment) {}

    @GetMapping
    public Map<String, Object> tracking(@PathVariable Long id, Authentication auth) {
        boolean staff = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SALES_ORDERS:VIEW"));
        return delivery.tracking(id, auth.getName(), staff);
    }

    @PostMapping("/tip")
    public Map<String, Object> tip(@PathVariable Long id, @RequestBody TipRequest req, Principal p) {
        return delivery.tip(id, req.amount(), p.getName());
    }

    @PostMapping("/tip/confirm")
    public Map<String, Object> confirmTip(@PathVariable Long id, @RequestBody TipRequest req, Principal p) {
        return delivery.confirmTip(id, req.amount(), p.getName());
    }

    @PostMapping("/feedback")
    public Map<String, Object> feedback(@PathVariable Long id, @RequestBody FeedbackRequest req, Principal p) {
        return delivery.feedback(id, req.rating() == null ? 0 : req.rating(), req.comment(), p.getName());
    }
}
