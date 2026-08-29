package com.hyperlocal.delivery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger / OpenAPI 3 configuration.
 *
 * <p>Advertises a single bearer-JWT security scheme applied at the
 * document level so every endpoint inherits it; {@code permitAll}
 * endpoints should override with an empty security requirement at the
 * operation annotation.
 */
@Configuration
@OpenAPIDefinition(tags = {
        @Tag(name = "Auth", description = "Registration, login, token refresh, logout"),
        @Tag(name = "Agents", description = "Delivery-agent CRUD and deactivation"),
        @Tag(name = "Shipments", description = "Shipment lifecycle and reassignment"),
        @Tag(name = "Delivery Attempts", description = "Per-attempt outcome logging"),
        @Tag(name = "Public Tracking", description = "Unauthenticated customer tracking"),
        @Tag(name = "Analytics", description = "Business KPI aggregates")
})
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hyperlocal Delivery Backend API")
                        .version("0.1.0")
                        .description("""
                                REST API for a multi-tenant last-mile delivery platform.
                                Businesses manage their own delivery agents and shipments;
                                customers receive a public tracking link.
                                """))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Access token issued by /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
