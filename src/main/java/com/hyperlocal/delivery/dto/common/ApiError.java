package com.hyperlocal.delivery.dto.common;

import java.util.UUID;

/**
 * Envelope for error responses.
 *
 * <p>Serializes to
 * {@code {"status":"error","code":"...","message":"...","field":"...","timestamp":"...","path":"...","reference":"..."}}.
 *
 * @param status    always {@code "error"}
 * @param code      machine-readable error code (see
 *                  {@code com.hyperlocal.delivery.exception.ErrorCode})
 * @param message   human-readable error message
 * @param field     optional offending field name (for validation errors);
 *                  may be {@code null}
 * @param timestamp ISO-8601 UTC timestamp of the error
 * @param path      request path that produced the error
 * @param reference short, server-generated id echoed in the server log line
 *                  for this error so a support agent can grep for it; every
 *                  error response carries one, generated here rather than by
 *                  the client, so it is always genuinely searchable
 */
public record ApiError(
        String status,
        String code,
        String message,
        String field,
        String timestamp,
        String path,
        String reference) {

    /**
     * Convenience factory that fills in {@code status = "error"} and a
     * fresh {@link #reference()}.
     */
    public static ApiError of(String code, String message, String field, String timestamp, String path) {
        return of(code, message, field, timestamp, path, generateReference());
    }

    /**
     * Same as {@link #of(String, String, String, String, String)} but with
     * an explicit {@code reference}, for callers that need to log it
     * alongside the response (e.g. the unhandled-exception fallback).
     */
    public static ApiError of(String code, String message, String field, String timestamp, String path,
            String reference) {
        return new ApiError("error", code, message, field, timestamp, path, reference);
    }

    /**
     * A short, support-legible reference code, e.g. {@code REQ-8F3A2C1D}.
     */
    public static String generateReference() {
        return "REQ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
