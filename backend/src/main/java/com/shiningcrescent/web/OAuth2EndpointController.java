package com.shiningcrescent.web;

import com.shiningcrescent.security.JwtService;
import com.shiningcrescent.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class OAuth2EndpointController {
    private final AuthService auth;
    private final JwtService jwt;
    private final ConcurrentHashMap<String, AuthCode> codes = new ConcurrentHashMap<>();

    @Value("${app.oauth2.client-id:shining-crescent-spa}")
    private String publicClientId;

    @Value("${app.oauth2.redirect-uris:https://54.175.201.2.sslip.io/,https://54.175.201.2.sslip.io,http://localhost:5173/,http://localhost:5173,http://127.0.0.1:5173/,http://127.0.0.1:5173}")
    private String redirectUris;

    @GetMapping("/oauth2/authorize")
    public ResponseEntity<?> authorize(
            @RequestParam(value = "response_type", required = false) String responseType,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            HttpServletRequest request) {
        if (responseType == null || responseType.isBlank() || clientId == null || clientId.isBlank()) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_request",
                    "authorization_endpoint requires response_type and client_id");
        }
        if (!publicClientId.equals(clientId)) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_client", "client_id is not registered");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_request", "redirect_uri is required");
        }
        if (!allowedRedirect(redirectUri)) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_request", "redirect_uri is not registered");
        }
        if ("code".equals(responseType)) {
            if (codeChallenge == null || codeChallenge.isBlank() || !"S256".equalsIgnoreCase(codeChallengeMethod)) {
                return oauthError(HttpStatus.BAD_REQUEST, "invalid_request",
                        "PKCE required: code_challenge and code_challenge_method=S256");
            }
        }
        String username = request.getUserPrincipal() == null ? null : request.getUserPrincipal().getName();
        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/oauth2/authorization/google")).build();
        }
        if ("token".equals(responseType) || "id_token".equals(responseType)) {
            Map<String, Object> me = auth.me(username);
            @SuppressWarnings("unchecked")
            java.util.List<String> roles = (java.util.List<String>) me.get("roles");
            String token = jwt.issue(username, roles == null ? java.util.List.of() : roles);
            String frag = "access_token=" + enc(token) + "&token_type=Bearer&expires_in=" + (jwt.expirationMs() / 1000);
            if (state != null) frag += "&state=" + enc(state);
            if (scope != null) frag += "&scope=" + enc(scope);
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(join(redirectUri, "#", frag))).build();
        }
        String code = UUID.randomUUID().toString().replace("-", "");
        codes.put(code, new AuthCode(username, Instant.now().plusSeconds(300), clientId, redirectUri, codeChallenge));
        String q = "code=" + enc(code);
        if (state != null) q += "&state=" + enc(state);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(join(redirectUri, "?", q))).build();
    }

    @PostMapping(value = "/oauth2/token", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<?> token(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "refresh_token", required = false) String refreshToken,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier,
            @RequestParam(value = "client_id", required = false) String clientId) {
        try {
            if ("authorization_code".equals(grantType)) {
                if (clientId != null && !clientId.isBlank() && !publicClientId.equals(clientId)) {
                    return oauthError(HttpStatus.BAD_REQUEST, "invalid_client", "client_id is not registered");
                }
                AuthCode stored = code == null ? null : codes.remove(code);
                if (stored == null || stored.expiresAt().isBefore(Instant.now())) {
                    return oauthError(HttpStatus.BAD_REQUEST, "invalid_grant", "authorization code is invalid or expired");
                }
                if (redirectUri != null && !redirectUri.equals(stored.redirectUri())) {
                    return oauthError(HttpStatus.BAD_REQUEST, "invalid_grant", "redirect_uri does not match");
                }
                if (!pkceValid(codeVerifier, stored.codeChallenge())) {
                    return oauthError(HttpStatus.BAD_REQUEST, "invalid_grant", "PKCE code_verifier failed");
                }
                return ResponseEntity.ok(tokenResponse(auth.me(stored.username())));
            }
            if ("refresh_token".equals(grantType)) {
                if (refreshToken == null || refreshToken.isBlank()) {
                    return oauthError(HttpStatus.BAD_REQUEST, "invalid_request", "refresh_token is required");
                }
                String user = jwt.username(refreshToken);
                return ResponseEntity.ok(tokenResponse(auth.me(user)));
            }
            return oauthError(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
                    "Supported grant_types: authorization_code, refresh_token");
        } catch (IllegalArgumentException ex) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_grant", ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tokenResponse(Map<String, Object> session) {
        String username = String.valueOf(session.get("username"));
        java.util.List<String> roles = session.get("roles") instanceof java.util.List<?> list
                ? (java.util.List<String>) list : java.util.List.of();
        String access = session.get("token") instanceof String t ? t : jwt.issue(username, roles);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", access);
        body.put("token_type", "Bearer");
        body.put("expires_in", jwt.expirationMs() / 1000);
        body.put("scope", "openid profile email");
        body.put("id_token", access);
        return body;
    }

    private boolean allowedRedirect(String redirectUri) {
        Set<String> allowed = Arrays.stream(redirectUris.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return allowed.contains(redirectUri);
    }

    private static boolean pkceValid(String verifier, String challenge) {
        if (verifier == null || verifier.isBlank() || challenge == null || challenge.isBlank()) {
            return false;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return MessageDigest.isEqual(computed.getBytes(StandardCharsets.US_ASCII),
                    challenge.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private static ResponseEntity<Map<String, Object>> oauthError(HttpStatus status, String error, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("error_description", description);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String join(String base, String sep, String extra) {
        if ("?".equals(sep) && base.contains("?")) {
            return base + "&" + extra;
        }
        return base + sep + extra;
    }

    private record AuthCode(String username, Instant expiresAt, String clientId, String redirectUri, String codeChallenge) {}
}
