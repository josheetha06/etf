package com.epictalenttrust.hr.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Checks a signed-in person on every API call: when an admin turns their access off, they are signed out straight
 * away; and someone with a one-time password can only choose a new password until they have done so.
 */
@Component
public class SessionGuardFilter extends OncePerRequestFilter {

    private record State(String status, boolean mustChangePassword) {
    }

    private final JdbcClient jdbc;

    public SessionGuardFilter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/public/") || path.startsWith("/api/auth/csrf")
                || path.startsWith("/api/auth/login") || path.startsWith("/api/auth/totp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof HrUserPrincipal principal)) {
            chain.doFilter(request, response);
            return;
        }
        State state = state(principal.id());
        if (state == null || "disabled".equals(state.status())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            com.epictalenttrust.hr.config.SecurityConfig.writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "user_disabled", "Your access has been turned off. Ask your HR admin.");
            return;
        }
        String path = request.getRequestURI();
        boolean allowed = path.startsWith("/api/auth/") || path.startsWith("/api/branding");
        if (state.mustChangePassword() && !allowed) {
            com.epictalenttrust.hr.config.SecurityConfig.writeProblem(response, HttpServletResponse.SC_FORBIDDEN,
                    "password_change_required", "Choose your own password to continue.");
            return;
        }
        chain.doFilter(request, response);
    }

    private State state(UUID userId) {
        return jdbc.sql("select status, must_change_password from hr_users where id = :id")
                .param("id", userId)
                .query((rs, row) -> new State(rs.getString("status"), rs.getBoolean("must_change_password")))
                .optional().orElse(null);
    }
}
