package com.shiningcrescent.service;

import com.shiningcrescent.config.StripeProperties;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Balance;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentIntentCollection;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentListParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class StripeGateway {
    private final StripeProperties stripe;
    private final AtomicReference<Map<String, Object>> lastWebhook = new AtomicReference<>();

    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("publishableKey", stripe.publishableKey() == null ? "" : stripe.publishableKey());
        m.put("mock", !stripe.configured());
        m.put("currency", stripe.currency() == null || stripe.currency().isBlank() ? "aed" : stripe.currency());
        m.put("webhookConfigured", stripe.webhookSecret() != null && !stripe.webhookSecret().isBlank());
        m.put("secretConfigured", stripe.configured());
        m.put("lastWebhook", lastWebhook.get());
        return m;
    }

    public void noteWebhook(String type, boolean ok, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type == null ? "" : type);
        m.put("ok", ok);
        m.put("detail", detail == null ? "" : detail);
        m.put("at", Instant.now().toString());
        lastWebhook.set(m);
    }

    public Map<String, Object> probe() {
        Map<String, Object> m = new LinkedHashMap<>(config());
        if (!stripe.configured()) {
            m.put("reachable", false);
            m.put("status", "mock");
            m.put("detail", "Stripe keys are not set. Checkout uses mock confirm.");
            return m;
        }
        try {
            ensureApiKey();
            Balance balance = Balance.retrieve();
            m.put("reachable", true);
            m.put("status", "live");
            m.put("livemode", Boolean.TRUE.equals(balance.getLivemode()));
            List<Map<String, Object>> available = new ArrayList<>();
            if (balance.getAvailable() != null) {
                balance.getAvailable().forEach(b -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("currency", b.getCurrency());
                    row.put("amount", b.getAmount());
                    available.add(row);
                });
            }
            m.put("available", available);
            PaymentIntentCollection list = PaymentIntent.list(
                    PaymentIntentListParams.builder().setLimit(6L).build());
            List<Map<String, Object>> recent = new ArrayList<>();
            if (list.getData() != null) {
                list.getData().forEach(pi -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", pi.getId());
                    row.put("status", pi.getStatus());
                    row.put("amount", pi.getAmount());
                    row.put("currency", pi.getCurrency());
                    recent.add(row);
                });
            }
            m.put("recentIntents", recent);
            m.put("detail", "Stripe API responded.");
        } catch (Exception e) {
            m.put("reachable", false);
            m.put("status", "error");
            m.put("detail", e.getMessage() == null ? "Stripe API did not respond." : e.getMessage());
        }
        return m;
    }

    public boolean mockMode() {
        return !stripe.configured();
    }

    public PaymentIntent createPaymentIntent(String orderNo, BigDecimal amountAed, String purpose) {
        ensureApiKey();
        long fils = toFils(amountAed);
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(fils)
                .setCurrency(currency())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
                .putMetadata("orderNo", orderNo)
                .putMetadata("purpose", purpose)
                .setDescription("Shining Crescent " + purpose + " " + orderNo)
                .build();
        try {
            return PaymentIntent.create(params);
        } catch (Exception e) {
            throw new IllegalStateException("Payment could not be started. Please try again.");
        }
    }

    public PaymentIntent retrieve(String paymentIntentId) {
        ensureApiKey();
        try {
            return PaymentIntent.retrieve(paymentIntentId);
        } catch (Exception e) {
            throw new IllegalStateException("We could not confirm this payment. Please try again.");
        }
    }

    public Event parseWebhook(String payload, String signature) {
        if (stripe.webhookSecret() == null || stripe.webhookSecret().isBlank()) {
            throw new IllegalStateException("Payment is not configured. Please try again later.");
        }
        try {
            return Webhook.constructEvent(payload, signature, stripe.webhookSecret());
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("This payment could not be verified.");
        }
    }

    private void ensureApiKey() {
        if (!stripe.configured()) {
            throw new IllegalStateException("Payment is not configured. Please try again later.");
        }
        Stripe.apiKey = stripe.secretKey();
    }

    private String currency() {
        return stripe.currency() == null || stripe.currency().isBlank() ? "aed" : stripe.currency();
    }

    public static long toFils(BigDecimal amountAed) {
        return amountAed.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
