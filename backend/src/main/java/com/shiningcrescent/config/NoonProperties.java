package com.shiningcrescent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.noon")
public record NoonProperties(boolean enabled, String baseUrl, String apiKey, String warehouseCode, String merchantCode) {
    public boolean live() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
