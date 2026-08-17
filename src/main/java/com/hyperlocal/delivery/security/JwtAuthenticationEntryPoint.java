package com.hyperlocal.delivery.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.hyperlocal.delivery.dto.common.ApiError;
import com.hyperlocal.delivery.util.TimeUtils;

/**
 * Converts an unauthenticated request into the standard 401 error
 * envelope. Invoked by Spring Security when a secured endpoint is hit
 * without a valid {@code Authentication} in the context, and also used
 * directly by {@link JwtAuthFilter} when a presented token is invalid
 * or expired.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        String message = authException != null && authException.getMessage() != null
                ? authException.getMessage()
                : "Authentication required";
        write(response, request, message, objectMapper);
    }

    /**
     * Write a 401 {@link ApiError} JSON body and commit the response.
     *
     * <p>Static so the JWT filter can call it without resolving a bean
     * when it needs to fail a request before the Spring Security
     * authentication pipeline runs.
     */
    public static void write(
            HttpServletResponse response,
            HttpServletRequest request,
            String message,
            ObjectMapper objectMapper) throws IOException {
        ApiError body = ApiError.of(
                "UNAUTHORIZED",
                message,
                null,
                TimeUtils.nowIso(),
                request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
