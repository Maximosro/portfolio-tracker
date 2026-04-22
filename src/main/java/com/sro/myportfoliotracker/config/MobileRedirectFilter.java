package com.sro.myportfoliotracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Redirects mobile user agents (iOS/Android) to the mobile-optimized read-only view.
 * Users can bypass with ?desktop=true query parameter or a cookie.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MobileRedirectFilter extends OncePerRequestFilter {

    private static final Pattern MOBILE_UA = Pattern.compile(
            "iPhone|iPad|iPod|Android.*Mobile|Android.*Chrome/[.0-9]* Mobile",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();

        // Only intercept root and index.html requests (servlet path excludes context-path)
        if ("/".equals(path) || "".equals(path) || "/index.html".equals(path)) {
            // Allow bypass with ?desktop=true
            if ("true".equals(request.getParameter("desktop"))) {
                // Set cookie so subsequent navigations stay on desktop
                jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("prefer_desktop", "true");
                cookie.setPath(request.getContextPath() + "/");
                cookie.setMaxAge(86400); // 24h
                response.addCookie(cookie);
                filterChain.doFilter(request, response);
                return;
            }

            // Check cookie
            if (request.getCookies() != null) {
                for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                    if ("prefer_desktop".equals(c.getName()) && "true".equals(c.getValue())) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                }
            }

            // Check User-Agent
            String ua = request.getHeader("User-Agent");
            if (ua != null && MOBILE_UA.matcher(ua).find()) {
                response.sendRedirect(request.getContextPath() + "/mobile.html");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}

