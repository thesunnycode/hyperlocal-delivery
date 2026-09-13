package com.hyperlocal.delivery.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

/**
 * The short-cache resource handler must name the root-level files the
 * frontend actually ships ({@code frontend/public} plus Vite's
 * {@code index.html}), not files that do not exist.
 */
class WebConfigTest {

    private static ResourceHandlerRegistry registry() {
        ResourceHandlerRegistry registry =
                new ResourceHandlerRegistry(new StaticApplicationContext(), new MockServletContext());
        new WebConfig().addResourceHandlers(registry);
        return registry;
    }

    @Test
    void shortCacheHandler_coversTheRootFilesFrontendPublicShips() {
        ResourceHandlerRegistry registry = registry();
        assertThat(registry.hasMappingForPattern("/index.html")).isTrue();
        assertThat(registry.hasMappingForPattern("/favicon.svg")).isTrue();
        assertThat(registry.hasMappingForPattern("/robots.txt")).isTrue();
        assertThat(registry.hasMappingForPattern("/delivery-rider-loop.webm")).isTrue();
    }

    @Test
    void shortCacheHandler_noLongerNamesFilesThatAreNotShipped() {
        ResourceHandlerRegistry registry = registry();
        assertThat(registry.hasMappingForPattern("/favicon.ico")).isFalse();
        assertThat(registry.hasMappingForPattern("/vite.svg")).isFalse();
    }

    @Test
    void hashedAssets_keepTheirImmutableHandler() {
        assertThat(registry().hasMappingForPattern("/assets/**")).isTrue();
    }
}
