package com.shiningcrescent.security;

import com.shiningcrescent.domain.entity.UserAccount;
import com.shiningcrescent.repo.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserAccountRepository users;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                String username = jwtService.username(token);
                UserAccount user = users.findByUsername(username).orElse(null);
                if (user != null && user.isActive()) {
                    var authorities = user.getRoles().stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getCode()))
                            .collect(Collectors.toList());
                    user.getRoles().forEach(role ->
                            role.getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p))));
                    var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    request.setAttribute("actor", username);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
