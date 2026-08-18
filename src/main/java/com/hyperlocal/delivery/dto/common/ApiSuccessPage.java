package com.hyperlocal.delivery.dto.common;

import java.util.List;

/**
 * Envelope for successful, paginated list responses.
 *
 * <p>Serializes to
 * {@code {"status":"success","data":[...],"pagination":{...}}}.
 *
 * @param status     always {@code "success"}
 * @param data       page content
 * @param pagination page metadata
 * @param <T>        element type of the list
 */
public record ApiSuccessPage<T>(String status, List<T> data, Pagination pagination) {

    /**
     * Convenience factory that fills in {@code status = "success"}.
     */
    public static <T> ApiSuccessPage<T> of(List<T> data, Pagination pagination) {
        return new ApiSuccessPage<>("success", data, pagination);
    }
}
