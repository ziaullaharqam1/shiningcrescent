package com.shiningcrescent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.delivery")
public record DeliveryProperties(double warehouseLat, double warehouseLng, long tickMs) {
}
