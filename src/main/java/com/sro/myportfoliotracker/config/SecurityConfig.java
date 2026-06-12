package com.sro.myportfoliotracker.config;

import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  @Value("${supabase.jwt.jwks-uri}")
  private String jwksUri;

  private final ObjectMapper mapper = new ObjectMapper();
  private JWKSet cachedJwkSet;
  private long cacheExpiry;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/**").authenticated()
            .anyRequest().permitAll()
        )
        .addFilterAfter(jwtAuthFilter(),
            org.springframework.security.web.context.SecurityContextHolderFilter.class)
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((request, response, authException) -> {
              response.setStatus(401);
              response.setContentType(MediaType.APPLICATION_JSON_VALUE);
              response.getWriter().write("{\"error\":\"No autorizado\"}");
            })
        );

    return http.build();
  }

  @Bean
  public Filter jwtAuthFilter() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(HttpServletRequest request,
          HttpServletResponse response, FilterChain chain) {
        try {
          String authHeader = request.getHeader("Authorization");
          if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            log.debug("Processing Bearer token: {}...", token.substring(0, Math.min(token.length(), 30)));

            // Parse header for kid
            String[] parts = token.split("\\.");
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            Map<String, Object> header = mapper.readValue(headerJson, Map.class);
            String kid = (String) header.get("kid");

            // Get JWK
            JWKSet jwkSet = getJwkSet();
            var key = jwkSet.getKeyByKeyId(kid);
            if (key == null) {
              log.warn("No JWK for kid: {}", kid);
              chain.doFilter(request, response);
              return;
            }
            ECPublicKey publicKey = ((ECKey) key).toECPublicKey();

            // Validate JWT
            var claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            log.debug("JWT validated. Subject: {}", claims.getSubject());

            // Build Spring Security authentication
            Jwt jwt = Jwt.withTokenValue(token)
                .headers(h -> h.put("alg", "ES256"))
                .subject(claims.getSubject())
                .issuedAt(claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null)
                .expiresAt(claims.getExpiration() != null ? claims.getExpiration().toInstant() : null)
                .build();

            List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_AUTHENTICATED")
            );

            JwtAuthenticationToken authToken = new JwtAuthenticationToken(jwt, authorities);
            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.debug("Authentication set for user: {}", claims.getSubject());
          }
        } catch (Exception e) {
          log.warn("JWT validation failed: {}", e.getMessage());
          // Leave security context as-is (anonymous)
        }
        try {
          chain.doFilter(request, response);
        } catch (Exception e) {
          log.error("Filter chain error: {}", e.getMessage());
        }
      }
    };
  }

  private JWKSet getJwkSet() throws Exception {
    long now = System.currentTimeMillis();
    if (cachedJwkSet != null && now < cacheExpiry) {
      return cachedJwkSet;
    }
    log.info("Fetching JWKS from {}", jwksUri);
    cachedJwkSet = JWKSet.load(new java.net.URI(jwksUri).toURL());
    cacheExpiry = now + 3600_000;
    log.info("JWKS loaded: {} keys", cachedJwkSet.getKeys().size());
    return cachedJwkSet;
  }
}
