package com.agency.api.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.agency.api.repository.AccountRepository;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final AccountRepository accounts;

    public JwtFilter(JwtService jwt, AccountRepository accounts) {
        this.jwt = jwt;
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer "))
            try {
                var account = accounts.findByEmailIgnoreCase(jwt.subject(header.substring(7))).orElse(null);
                if (account != null) {
                    String path = request.getServletPath();
                    if (path == null || path.isBlank()) {
                        String uri = request.getRequestURI(), context = request.getContextPath();
                        path = uri.startsWith(context) ? uri.substring(context.length()) : uri;
                    }
                    if (account.isMustChangePassword() && !path.equals("/api/auth/change-password")
                            && !path.equals("/api/auth/login")) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter()
                                .write("{\"error\":\"Actualize a palavra-passe temporária antes de continuar.\"}");
                        return;
                    }
                    SecurityContextHolder.getContext()
                            .setAuthentication(new UsernamePasswordAuthenticationToken(account.getEmail(), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))));
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        chain.doFilter(request, response);
    }
}
