package com.hyperlocal.delivery.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Time helpers used throughout the service. All values are UTC.
 *
 * <p>Not instantiable.
 */
public final class TimeUtils {

    private TimeUtils() {
        // no instances
    }

    /**
     * Current instant in UTC.
     */
    public static Instant nowUtc() {
        return Instant.now();
    }

    /**
     * Format an {@link Instant} as an ISO-8601 string
     * (e.g. {@code 2024-01-15T10:30:00Z}).
     */
    public static String toIso(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    /**
     * ISO-8601 UTC timestamp for "now".
     */
    public static String nowIso() {
        return toIso(nowUtc());
    }

    /**
     * Format a {@link LocalDateTime} as an ISO-8601 UTC string with a
     * {@code Z} suffix (e.g. {@code 2024-01-15T10:30:00Z}).
     *
     * <p>Every {@code LocalDateTime} in this codebase represents a UTC
     * instant (see class doc) but carries no timezone marker of its own, so
     * {@link LocalDateTime#toString()} produces an offset-less string that
     * browsers parse as local time instead of UTC. Route every
     * entity-to-DTO timestamp conversion through here instead of calling
     * {@code toString()} directly.
     */
    public static String toIso(LocalDateTime dateTime) {
        return dateTime != null ? toIso(dateTime.toInstant(ZoneOffset.UTC)) : null;
    }

    /**
     * Today's date in UTC.
     */
    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /**
     * Start of day ({@code 00:00:00.000000000}) for the given date.
     */
    public static LocalDateTime startOfDay(LocalDate d) {
        return LocalDateTime.of(d, LocalTime.MIN);
    }

    /**
     * End of day ({@code 23:59:59.999999999}) for the given date.
     */
    public static LocalDateTime endOfDay(LocalDate d) {
        return LocalDateTime.of(d, LocalTime.MAX);
    }
}
