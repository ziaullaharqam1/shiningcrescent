package com.risingcrescent.security;

import com.risingcrescent.config.GoogleProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class GoogleOAuthFailureHandler implements AuthenticationFailureHandler {
    private final GoogleProperties google;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String base = google.frontendRedirectOrDefault();
        String sep = base.contains("?") ? "&" : "?";
        response.sendRedirect(base + sep + "error=" + URLEncoder.encode("google_failed", StandardCharsets.UTF_8));
    }
}
