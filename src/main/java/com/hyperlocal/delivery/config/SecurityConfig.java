package com.hyperlocal.delivery.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.hyperlocal.delivery.security.JwtAuthFilter;
import com.hyperlocal.delivery.security.JwtAuthenticationEntryPoint;
import com.hyperlocal.delivery.security.PublicApiPaths;

/**
 * Central Spring Security configuration.
 *
 * <ul>
 *   <li>Stateless session management — JWT only, no cookies.</li>
 *   <li>CSRF disabled (no cookie-based auth surface).</li>
 *   <li>CORS driven by {@link CorsConfigProperties}.</li>
 *   <li>Public endpoints: auth entry points, public tracking,
 *       Swagger UI / OpenAPI docs, actuator health/info, and the plain
 *       {@code /api/health} smoke-test endpoint.</li>
 *   <li>The built SPA shell (static assets, {@code index.html}, and any
 *       non-{@code /api}/{@code /actuator} route path forwarded to it by
 *       {@link com.hyperlocal.delivery.controller.SpaFallbackController})
 *       is also public &mdash; the page loads for everyone and React Router
 *       + the API's own auth checks gate access to actual data.</li>
 *   <li>All other endpoints (the {@code /api/**} surface) require a valid
 *       access token; role-based authorisation is applied at the method
 *       level via {@code @PreAuthorize}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(CorsConfigProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthFilter jwtFilter,
            JwtAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'"))
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .contentTypeOptions(cto -> {})
                )
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth
                        // The invite token IS the credential for accept-invite: the
                        // agent has no password until they use it. See PublicApiPaths
                        // for the full list — also consulted by JwtAuthFilter so a
                        // stale/invalid bearer token can't 401 one of these paths.
                        .requestMatchers(PublicApiPaths.asArray()).permitAll()
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.ico",
                                "/vite.svg",
                                "/{path:^(?!api|actuator).*$}",
                                "/{path:^(?!api|actuator).*$}/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsConfigProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        String raw = props.allowedOrigins() == null ? "*" : props.allowedOrigins().trim();
        if (raw.isEmpty() || "*".equals(raw)) {
            cfg.setAllowedOriginPatterns(List.of("*"));
            cfg.setAllowCredentials(false);
        } else {
            List<String> origins = Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            cfg.setAllowedOrigins(origins);
            cfg.setAllowCredentials(true);
        }
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
