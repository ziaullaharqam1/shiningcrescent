package com.shiningcrescent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.stripe")
public record StripeProperties(String secretKey, String publishableKey, String webhookSecret, String currency) {
    public boolean configured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
