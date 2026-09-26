package com.shiningcrescent.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class BearerAuthEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (path.equals("/api") || path.startsWith("/api/")) {
            String proto = header(request, "X-Forwarded-Proto", request.getScheme());
            String host = header(request, "X-Forwarded-Host", request.getHeader("Host"));
            if (host == null || host.isBlank()) {
                host = request.getServerName();
            }
            String realm = proto + "://" + host.split(",")[0].trim();
            response.setHeader("WWW-Authenticate", "Bearer realm=\"" + realm + "\"");
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private static String header(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
