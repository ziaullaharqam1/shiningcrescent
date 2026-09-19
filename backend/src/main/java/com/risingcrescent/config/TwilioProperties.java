package com.risingcrescent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.twilio")
public record TwilioProperties(String accountSid, String authToken, String fromNumber) {
    public boolean configured() {
        return notBlank(accountSid) && notBlank(authToken) && notBlank(fromNumber);
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
