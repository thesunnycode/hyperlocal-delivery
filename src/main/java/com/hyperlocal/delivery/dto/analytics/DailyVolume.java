package com.hyperlocal.delivery.dto.analytics;

import lombok.Getter;

/**
 * Daily shipment volume breakdown. Constructed from native query results
 * where MySQL {@code DATE()} returns a {@link java.sql.Date}.
 */
@Getter
public class DailyVolume {

    private final String date;
    private final long total;
    private final long delivered;
    private final long failed;
    private final long inProgress;

    public DailyVolume(java.sql.Date date, long total, long delivered, long failed, long inProgress) {
        this.date = date != null ? date.toLocalDate().toString() : null;
        this.total = total;
        this.delivered = delivered;
        this.failed = failed;
        this.inProgress = inProgress;
    }

    /**
     * Alternate constructor accepting a String date directly (for cases
     * where the native query returns a String).
     */
    public DailyVolume(String date, long total, long delivered, long failed, long inProgress) {
        this.date = date;
        this.total = total;
        this.delivered = delivered;
        this.failed = failed;
        this.inProgress = inProgress;
    }
}
