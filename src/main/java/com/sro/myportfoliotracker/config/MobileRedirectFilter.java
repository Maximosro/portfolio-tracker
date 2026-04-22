package com.sro.myportfoliotracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class MobileRedirectFilter extends OncePerRequestFilter {

    private static final Pattern MOBILE_UA = Pattern.compile(
            "iPhone|iPad|iPod|Android",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        // Path relative to context: /portfoliotracker/ → /, /portfoliotracker/index.html → /index.html
        String path = uri.substring(contextPath.length());

        log.debug("MobileRedirectFilter — URI: {}, contextPath: {}, path: {}, servletPath: {}",
                uri, contextPath, path, request.getServletPath());

        // Only intercept root and index.html requests
        if ("/".equals(path) || "".equals(path) || "/index.html".equals(path)) {
            String ua = request.getHeader("User-Agent");
            log.debug("MobileRedirectFilter — Checking mobile redirect. UA: {}", ua);

            // Allow bypass with ?desktop=true
            if ("true".equals(request.getParameter("desktop"))) {
                jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("prefer_desktop", "true");
                cookie.setPath(contextPath.isEmpty() ? "/" : contextPath + "/");
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
            if (ua != null && MOBILE_UA.matcher(ua).find()) {
                String redirectUrl = contextPath + "/mobile.html";
                log.info("MobileRedirectFilter — Mobile UA detected, redirecting to {}", redirectUrl);
                response.sendRedirect(redirectUrl);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}

