package com.hyperlocal.delivery.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration: registers custom {@code Converter}s and static
 * asset cache headers for CDN-readiness.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new ShipmentStatusConverter());
    }

    /**
     * Hashed Vite assets (immutable filenames) get aggressive 1-year
     * caching. Non-hashed assets (index.html, favicon) get short caching
     * with revalidation. This makes the SPA CDN-ready without a separate
     * nginx layer.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Hashed JS/CSS bundles — immutable, cache aggressively
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        // Root-level static files (index.html, favicon) — short cache
        registry.addResourceHandler("/index.html", "/favicon.ico", "/vite.svg")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic());
    }
}
