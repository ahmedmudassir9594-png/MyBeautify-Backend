package com.cosmetics.ecommerce.config;

import com.cosmetics.ecommerce.enums.AuditAction;
import com.cosmetics.ecommerce.security.JwtAuthenticationFilter;
import com.cosmetics.ecommerce.repository.UserRepository;
import com.cosmetics.ecommerce.service.AuditLogService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.Arrays;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final String allowedOrigins;
    private final ObjectProvider<AuditLogService> auditLogServiceProvider;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          @Value("${app.cors.allowed-origins:${CORS_ALLOWED_ORIGINS:https://www.fashionmalkaz.com,https://fashionmalkaz.com,https://malkaz.vercel.app,http://localhost:5173,http://localhost:3000}}") String allowedOrigins,
                          ObjectProvider<AuditLogService> auditLogServiceProvider) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.allowedOrigins = allowedOrigins;
        this.auditLogServiceProvider = auditLogServiceProvider;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. Preflight CORS
                        .requestMatchers(org.springframework.web.cors.CorsUtils::isPreFlightRequest).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 2. Health & Webhooks
                        .requestMatchers("/healthz", "/actuator/health").permitAll()
                        .requestMatchers("/payments/razorpay/webhook", "/webhooks/razorpay", "/payments/webhook", "/webhooks/**").permitAll()
                        .requestMatchers("/referrals/validate").permitAll()

                        // 3. Public Auth Routes
                        .requestMatchers("/auth/**").permitAll()

                        // 4. API Docs / Swagger
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // 5. Public Catalog (Both bare paths and subpaths included)
                        .requestMatchers(HttpMethod.GET,
                                "/products", "/products/**",
                                "/categories", "/categories/**",
                                "/categoriesASC", "/categoriesASC/**",
                                "/subcategories", "/subcategories/**",
                                "/combos", "/combos/**",
                                "/coupons", "/coupons/**",
                                "/uploads/**"
                        ).permitAll()

                        // 6. Public Combo Preview & Coupon Apply
                        .requestMatchers(HttpMethod.POST, "/combos/custom/preview", "/coupons/apply").permitAll()

                        // 7. Role Protected Endpoints
                        .requestMatchers("/admin/payments/**").hasRole("ADMIN")
                        .requestMatchers("/admin/audit-logs/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "STAFF")

                        // 8. Remaining Routes Authenticated
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            AuditLogService audit = auditLogServiceProvider.getIfAvailable();
                            if (audit != null) {
                                try {
                                    audit.logSecurityFailure(AuditAction.SECURITY_AUTHENTICATION_FAILURE, null,
                                            "Unauthenticated access attempted on " + request.getRequestURI());
                                } catch (Exception ignored) {}
                            }
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            try (PrintWriter writer = response.getWriter()) {
                                writer.write("{\"success\":false,\"message\":\"Full authentication is required to access this resource.\",\"status\":401,\"timestamp\":\"" + Instant.now() + "\"}");
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            AuditLogService audit = auditLogServiceProvider.getIfAvailable();
                            if (audit != null) {
                                try {
                                    audit.logSecurityFailure(AuditAction.SECURITY_ACCESS_DENIED, null,
                                            "Access denied on " + request.getRequestURI());
                                } catch (Exception ignored) {}
                            }
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            try (PrintWriter writer = response.getWriter()) {
                                writer.write("{\"success\":false,\"message\":\"You do not have permission to perform this action.\",\"status\":403,\"timestamp\":\"" + Instant.now() + "\"}");
                            }
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    org.springframework.security.core.userdetails.UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByEmailIgnoreCase(username)
                .filter(com.cosmetics.ecommerce.entity.User::isEnabled)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found or disabled."));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With", "X-Correlation-ID", "X-Forwarded-Proto"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Set-Cookie"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}