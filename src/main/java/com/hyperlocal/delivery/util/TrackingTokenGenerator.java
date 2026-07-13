package com.hyperlocal.delivery.util;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Generates opaque tracking tokens used as the public identifier of a
 * {@code Shipment}.
 *
 * <p>Exposed as a Spring component rather than a static utility so tests
 * can substitute a deterministic implementation.
 */
@Component
public class TrackingTokenGenerator {

    /**
     * Produce a fresh random tracking token.
     *
     * @return a random UUID (version 4) as its canonical 36-character
     *         string form
     */
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
