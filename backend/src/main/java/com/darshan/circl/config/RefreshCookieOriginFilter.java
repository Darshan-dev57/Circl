package com.darshan.circl.config;

import com.darshan.circl.identity.RefreshCookie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * SameSite cookies are useful defence in depth, but cookie-authenticated state changes
 * also require an explicit allowed Origin. The reverse proxy must preserve Origin.
 */
public final class RefreshCookieOriginFilter extends OncePerRequestFilter {

    private final List<String> allowedOrigins;

    public RefreshCookieOriginFilter(List<String> allowedOrigins) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!("POST".equalsIgnoreCase(request.getMethod())
                && (path.equals("/api/v1/auth/refresh") || path.equals("/api/v1/auth/logout")))) {
            return true;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return true;
        for (Cookie cookie : cookies) {
            if (RefreshCookie.NAME.equals(cookie.getName())) return false;
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins.contains(origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Refresh cookie Origin is not allowed");
            return;
        }
        chain.doFilter(request, response);
    }
}
