package com.risingcrescent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({StripeProperties.class, NoonProperties.class, DeliveryProperties.class,
        TwilioProperties.class, GoogleProperties.class})
public class IntegrationConfig {
    @Bean
    RestClient noonRestClient(NoonProperties noon) {
        return RestClient.builder()
                .baseUrl(noon.baseUrl() == null || noon.baseUrl().isBlank()
                        ? "https://noon-api-gateway.noon.partners/fbpi"
                        : noon.baseUrl())
                .defaultHeader("User-Agent", "RisingCrescent/1.0")
                .build();
    }
}
