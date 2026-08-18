package com.hyperlocal.delivery.dto.common;

/**
 * Pagination metadata block attached to paged responses.
 *
 * @param page          zero-based page index
 * @param size          page size
 * @param totalElements total number of elements across all pages
 * @param totalPages    total number of pages
 * @param hasNext       {@code true} if a next page exists
 * @param hasPrevious   {@code true} if a previous page exists
 */
public record Pagination(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {

    /**
     * Build a {@link Pagination} from a Spring Data {@link org.springframework.data.domain.Page}.
     */
    public static Pagination from(org.springframework.data.domain.Page<?> p) {
        return new Pagination(
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages(),
                p.hasNext(),
                p.hasPrevious());
    }
}
