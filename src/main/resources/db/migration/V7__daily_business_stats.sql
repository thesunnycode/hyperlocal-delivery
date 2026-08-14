-- V7__daily_business_stats.sql
-- Pre-computed daily aggregates table for analytics scalability.
-- As shipment volume grows (50K+ per business), real-time aggregate queries
-- over the raw shipments table become expensive. This table holds one row per
-- business per day, rolled up nightly by ScheduledCleanupJob.
--
-- Analytics endpoints can read from this table instead of scanning raw
-- shipments — O(days) instead of O(shipments).

CREATE TABLE daily_business_stats (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    business_id     BIGINT      NOT NULL,
    stat_date       DATE        NOT NULL,
    total_created   INT         NOT NULL DEFAULT 0,
    total_delivered INT         NOT NULL DEFAULT 0,
    total_failed    INT         NOT NULL DEFAULT 0,
    total_returned  INT         NOT NULL DEFAULT 0,
    avg_delivery_hours DOUBLE   NULL DEFAULT NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_daily_business_stats  PRIMARY KEY (id),
    CONSTRAINT uq_daily_stats_biz_date  UNIQUE (business_id, stat_date),
    CONSTRAINT fk_daily_stats_business  FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_daily_stats_date ON daily_business_stats (stat_date);
