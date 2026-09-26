package com.shiningcrescent.web;

import com.shiningcrescent.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OidcDiscoveryController {
    private final JwtService jwt;

    @Value("${app.oidc.issuer:}")
    private String configuredIssuer;

    @GetMapping(value = {"/.well-known/openid-configuration", "/.well-known/oauth-authorization-server"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> discovery(HttpServletRequest request) {
        String issuer = issuer(request);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("issuer", issuer);
        doc.put("authorization_endpoint", issuer + "/oauth2/authorize");
        doc.put("token_endpoint", issuer + "/oauth2/token");
        doc.put("jwks_uri", issuer + "/.well-known/jwks.json");
        doc.put("response_types_supported", List.of("code", "token", "id_token"));
        doc.put("subject_types_supported", List.of("public"));
        doc.put("id_token_signing_alg_values_supported", List.of("RS256"));
        doc.put("token_endpoint_auth_methods_supported", List.of("none"));
        doc.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        doc.put("scopes_supported", List.of("openid", "profile", "email"));
        doc.put("code_challenge_methods_supported", List.of("S256"));
        return doc;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return jwt.jwks();
    }

    private String issuer(HttpServletRequest request) {
        if (configuredIssuer != null && !configuredIssuer.isBlank()) {
            return configuredIssuer.trim().replaceAll("/+$", "");
        }
        if (jwt.issuer() != null && !jwt.issuer().isBlank()) {
            return jwt.issuer();
        }
        String proto = header(request, "X-Forwarded-Proto", request.getScheme());
        String host = header(request, "X-Forwarded-Host", request.getHeader("Host"));
        if (host == null || host.isBlank()) {
            host = "localhost:" + request.getServerPort();
        }
        return proto + "://" + host.split(",")[0].trim();
    }

    private static String header(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
