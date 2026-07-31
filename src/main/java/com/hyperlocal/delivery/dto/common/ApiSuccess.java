package com.hyperlocal.delivery.dto.common;

/**
 * Envelope for successful, single-payload responses.
 *
 * <p>Serializes to {@code {"status":"success","data":...}}.
 *
 * @param status always {@code "success"}
 * @param data   response payload
 * @param <T>    payload type
 */
public record ApiSuccess<T>(String status, T data) {

    /**
     * Convenience factory that fills in {@code status = "success"}.
     */
    public static <T> ApiSuccess<T> of(T data) {
        return new ApiSuccess<>("success", data);
    }
}
