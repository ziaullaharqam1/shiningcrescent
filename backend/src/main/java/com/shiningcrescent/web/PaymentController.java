package com.shiningcrescent.web;

import com.shiningcrescent.service.OrderService;
import com.shiningcrescent.service.StripeGateway;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final StripeGateway stripe;
    private final OrderService orders;

    @GetMapping("/config")
    public Map<String, Object> config() {
        return stripe.config();
    }

    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload,
                                          @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        Event event;
        try {
            event = stripe.parseWebhook(payload, signature == null ? "" : signature);
        } catch (RuntimeException e) {
            stripe.noteWebhook("invalid", false, e.getMessage());
            throw e;
        }
        stripe.noteWebhook(event.getType(), true, null);
        StripeObject obj = event.getDataObjectDeserializer().getObject().orElse(null);
        if (obj instanceof PaymentIntent pi && "payment_intent.succeeded".equals(event.getType())) {
            String purpose = pi.getMetadata() != null ? pi.getMetadata().get("purpose") : "order";
            if (purpose == null || "order".equals(purpose)) {
                String orderNo = pi.getMetadata() != null ? pi.getMetadata().get("orderNo") : null;
                if (orderNo != null) {
                    orders.markPaidByOrderNo(orderNo, pi.getId());
                } else {
                    orders.markPaidByIntent(pi.getId());
                }
            }
        }
        return ResponseEntity.ok("ok");
    }

    public record ConfirmRequest(String paymentIntentId) {}

    @PostMapping("/orders/{id}/confirm")
    public Map<String, Object> confirm(@PathVariable Long id, @RequestBody(required = false) ConfirmRequest body, Principal p) {
        return orders.confirmStripe(id, body == null ? null : body.paymentIntentId(), p.getName());
    }
}
