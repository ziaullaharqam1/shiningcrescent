package com.risingcrescent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.wapi")
public class WapiProperties {
    private boolean enabled = false;
    private String token = "";
    private String phoneNumberId = "";
    private String verifyToken = "risingcrescent";
    private String graphUrl = "https://graph.facebook.com/v21.0";
    private String defaultUsername = "admin";
}
