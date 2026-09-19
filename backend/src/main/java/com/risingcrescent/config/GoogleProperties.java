package com.risingcrescent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.google")
public record GoogleProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String loginUrl,
        String frontendRedirect
) {
    public boolean configured() {
        return notBlank(clientId) && notBlank(clientSecret);
    }

    public String loginUrlOrDefault() {
        return notBlank(loginUrl) ? loginUrl : "/oauth2/authorization/google";
    }

    public String frontendRedirectOrDefault() {
        return notBlank(frontendRedirect) ? frontendRedirect : "http://localhost:5173/login";
    }

    public String redirectUriOrDefault() {
        return notBlank(redirectUri) ? redirectUri : "http://localhost:8080/login/oauth2/code/google";
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }
}
