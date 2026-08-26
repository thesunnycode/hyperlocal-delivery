package com.hyperlocal.delivery.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards deep-link requests for client-side (TanStack Router) routes to the
 * built SPA's {@code index.html}, so a hard refresh on e.g.
 * {@code /agent/shipments/2} doesn't 404 against Spring's static resource
 * handler.
 *
 * <p>Only applies in builds where the frontend has been built into
 * {@code src/main/resources/static} (see {@code pom.xml}'s
 * {@code frontend-maven-plugin} binding and {@code frontend/vite.config.ts}'s
 * {@code build.outDir}, which points there directly). In a checkout with no built frontend, these
 * mappings simply forward to a missing {@code index.html} and Spring
 * returns its usual 404 &mdash; no different from today.
 *
 * <p>The path patterns exclude:
 * <ul>
 *   <li>{@code /api/**} and {@code /actuator/**} — real backend endpoints;</li>
 *   <li>{@code /swagger-ui/**} — Swagger UI resources;</li>
 *   <li>{@code /assets/**} — the SPA's hashed JS/CSS bundle, served directly
 *       by Spring's static resource handler;</li>
 *   <li>any path whose first segment contains a {@code .} — e.g.
 *       {@code /favicon.svg}, {@code /robots.txt} &mdash; so real static files
 *       at the root are served normally instead of being swallowed by this
 *       fallback (annotated {@code @Controller} mappings are matched before
 *       Spring Boot's static resource handler, so an over-broad pattern here
 *       would shadow those files).</li>
 * </ul>
 */
@Controller
public class SpaFallbackController {

    private static final String EXCLUDE_PATTERN = "^(?!api|actuator|assets|swagger-ui)[^.]*$";

    @RequestMapping(value = "/{path:" + EXCLUDE_PATTERN + "}")
    public String forwardRootRoute() {
        return "forward:/index.html";
    }

    @RequestMapping(value = "/{path:" + EXCLUDE_PATTERN + "}/**")
    public String forwardNestedRoute() {
        return "forward:/index.html";
    }
}
