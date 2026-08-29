package com.hyperlocal.delivery.config;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Minimal unauthenticated smoke-test endpoint. Complements the Actuator
 * {@code /actuator/health} probe but is guaranteed to stay on a fixed
 * path regardless of future actuator customisation.
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Service liveness probe")
public class HealthController {

    /**
     * Returns {@code {"status":"UP"}}. Does not verify downstream
     * dependencies; use {@code /actuator/health} for a deeper probe.
     */
    @GetMapping
    @Operation(summary = "Liveness probe")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
