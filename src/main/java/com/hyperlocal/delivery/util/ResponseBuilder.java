package com.hyperlocal.delivery.util;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import com.hyperlocal.delivery.dto.common.ApiError;
import com.hyperlocal.delivery.dto.common.ApiSuccess;
import com.hyperlocal.delivery.dto.common.ApiSuccessPage;
import com.hyperlocal.delivery.dto.common.Pagination;
import com.hyperlocal.delivery.exception.ErrorCode;

/**
 * Static factories for producing the canonical API response envelopes
 * ({@link ApiSuccess}, {@link ApiSuccessPage}, {@link ApiError}).
 *
 * <p>Not instantiable.
 */
public final class ResponseBuilder {

    private ResponseBuilder() {
        // no instances
    }

    /**
     * Wrap a payload in the success envelope.
     */
    public static <T> ApiSuccess<T> success(T data) {
        return ApiSuccess.of(data);
    }

    /**
     * Wrap a Spring Data {@link Page} of already-mapped DTOs in the
     * paginated success envelope.
     */
    public static <T> ApiSuccessPage<T> page(Page<T> page) {
        return ApiSuccessPage.of(page.getContent(), Pagination.from(page));
    }

    /**
     * Wrap a Spring Data {@link Page} of source entities in the paginated
     * success envelope, mapping each element to a DTO via {@code mapper}.
     */
    public static <T, R> ApiSuccessPage<R> page(Page<T> source, Function<T, R> mapper) {
        List<R> mapped = source.getContent().stream().map(mapper).toList();
        return ApiSuccessPage.of(mapped, Pagination.from(source));
    }

    /**
     * Build an error envelope. The timestamp is captured at call time as
     * the current UTC instant formatted as ISO-8601.
     */
    public static ApiError error(ErrorCode code, String message, String field, String path) {
        return ApiError.of(code.name(), message, field, TimeUtils.nowIso(), path);
    }
}
