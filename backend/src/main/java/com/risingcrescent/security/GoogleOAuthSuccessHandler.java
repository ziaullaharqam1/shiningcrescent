package com.risingcrescent.security;

import com.risingcrescent.config.GoogleProperties;
import com.risingcrescent.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {
    private final AuthService auth;
    private final GoogleProperties google;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String subject = principal.getAttribute("sub");
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");
        if (principal instanceof OidcUser oidc) {
            if (subject == null) subject = oidc.getSubject();
            if (email == null) email = oidc.getEmail();
            if (name == null) name = oidc.getFullName();
        }
        Map<String, Object> payload;
        try {
            payload = auth.loginGoogleUser(subject, email, name);
        } catch (Exception ex) {
            String base = google.frontendRedirectOrDefault();
            String sep = base.contains("?") ? "&" : "?";
            String code = "google_failed";
            String detail = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (detail.contains("disabled")) code = "account_disabled";
            else if (detail.contains("email")) code = "google_no_email";
            response.sendRedirect(base + sep + "error=" + URLEncoder.encode(code, StandardCharsets.UTF_8));
            return;
        }
        String token = String.valueOf(payload.get("token"));
        String base = google.frontendRedirectOrDefault();
        String sep = base.contains("?") ? "&" : "?";
        String dest = base + sep + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8) + "&oauth=1";
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(dest);
    }
}
