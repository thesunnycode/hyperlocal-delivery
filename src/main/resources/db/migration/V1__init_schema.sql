-- V1__init_schema.sql
-- Initial schema for the hyperlocal delivery backend: six tables in FK-dependency order.
-- References:
--   .kiro/specs/hyperlocal-delivery-backend/design.md#data-models
--   HyperlocalDelivery_DBSchema.docx (section 04)
--
-- Conventions:
--   * All tables use ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci.
--   * ENUMs are inline MySQL ENUM columns, mapped in JPA via @Enumerated(EnumType.STRING).
--   * Soft delete: `businesses` and `users` carry `deleted_at`; shipments/events are never soft-deleted.
--   * `shipment_events` and `delivery_attempts` are immutable (no `updated_at`).
--   * Unique constraints and companion indexes are both emitted per the design, even where the
--     UNIQUE constraint already implies an index; matches the DB schema doc verbatim.

-- -----------------------------------------------------------------------------
-- 1. businesses
-- -----------------------------------------------------------------------------
CREATE TABLE businesses (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255)  NOT NULL,
    email           VARCHAR(255)  NOT NULL,
    password_hash   VARCHAR(255)  NOT NULL,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at      DATETIME      NULL     DEFAULT NULL,
    created_at      DATETIME      NOT NULL,
    updated_at      DATETIME      NOT NULL,
    CONSTRAINT pk_businesses        PRIMARY KEY (id),
    CONSTRAINT uq_businesses_email  UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_businesses_email ON businesses (email);

-- -----------------------------------------------------------------------------
-- 2. users
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id              BIGINT                                    NOT NULL AUTO_INCREMENT,
    business_id     BIGINT                                    NOT NULL,
    email           VARCHAR(255)                              NOT NULL,
    password_hash   VARCHAR(255)                              NOT NULL,
    role            ENUM('BUSINESS_OWNER','DELIVERY_AGENT')   NOT NULL,
    full_name       VARCHAR(255)                              NOT NULL,
    phone           VARCHAR(20)                               NOT NULL,
    is_active       BOOLEAN                                   NOT NULL DEFAULT TRUE,
    deleted_at      DATETIME                                  NULL     DEFAULT NULL,
    created_at      DATETIME                                  NOT NULL,
    updated_at      DATETIME                                  NOT NULL,
    CONSTRAINT pk_users             PRIMARY KEY (id),
    CONSTRAINT uq_users_email       UNIQUE (email),
    CONSTRAINT fk_users_businesses  FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_users_business_id   ON users (business_id);
CREATE INDEX idx_users_business_role ON users (business_id, role);

-- -----------------------------------------------------------------------------
-- 3. shipments
-- -----------------------------------------------------------------------------
CREATE TABLE shipments (
    id                      BIGINT                                                                                              NOT NULL AUTO_INCREMENT,
    tracking_token          VARCHAR(36)                                                                                         NOT NULL,
    business_id             BIGINT                                                                                              NOT NULL,
    assigned_agent_id       BIGINT                                                                                              NULL,
    status                  ENUM('CREATED','ASSIGNED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED') NOT NULL DEFAULT 'CREATED',
    customer_name           VARCHAR(255)                                                                                        NOT NULL,
    customer_phone          VARCHAR(20)                                                                                         NOT NULL,
    delivery_address        TEXT                                                                                                NOT NULL,
    scheduled_delivery_at   DATETIME                                                                                            NULL     DEFAULT NULL,
    delivered_at            DATETIME                                                                                            NULL     DEFAULT NULL,
    created_at              DATETIME                                                                                            NOT NULL,
    updated_at              DATETIME                                                                                            NOT NULL,
    CONSTRAINT pk_shipments             PRIMARY KEY (id),
    CONSTRAINT uq_shipments_token       UNIQUE (tracking_token),
    CONSTRAINT fk_shipments_businesses  FOREIGN KEY (business_id)       REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT fk_shipments_users       FOREIGN KEY (assigned_agent_id) REFERENCES users (id)      ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_shipments_tracking_token   ON shipments (tracking_token);
CREATE INDEX idx_shipments_business_id      ON shipments (business_id);
CREATE INDEX idx_shipments_agent_status     ON shipments (assigned_agent_id, status);
CREATE INDEX idx_shipments_business_status  ON shipments (business_id, status);
CREATE INDEX idx_shipments_created_at       ON shipments (created_at);

-- -----------------------------------------------------------------------------
-- 4. shipment_events (immutable; no updated_at)
-- -----------------------------------------------------------------------------
CREATE TABLE shipment_events (
    id                      BIGINT                                                                                              NOT NULL AUTO_INCREMENT,
    shipment_id             BIGINT                                                                                              NOT NULL,
    from_status             ENUM('CREATED','ASSIGNED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED') NULL     DEFAULT NULL,
    to_status               ENUM('CREATED','ASSIGNED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED') NOT NULL,
    changed_by_user_id      BIGINT                                                                                              NOT NULL,
    notes                   TEXT                                                                                                NULL     DEFAULT NULL,
    created_at              DATETIME                                                                                            NOT NULL,
    CONSTRAINT pk_shipment_events   PRIMARY KEY (id),
    CONSTRAINT fk_events_shipments  FOREIGN KEY (shipment_id)        REFERENCES shipments (id) ON DELETE CASCADE,
    CONSTRAINT fk_events_users      FOREIGN KEY (changed_by_user_id) REFERENCES users (id)     ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_events_shipment_id      ON shipment_events (shipment_id);
CREATE INDEX idx_events_shipment_created ON shipment_events (shipment_id, created_at);

-- -----------------------------------------------------------------------------
-- 5. delivery_attempts (immutable; no updated_at)
-- -----------------------------------------------------------------------------
CREATE TABLE delivery_attempts (
    id                  BIGINT                                                              NOT NULL AUTO_INCREMENT,
    shipment_id         BIGINT                                                              NOT NULL,
    agent_id            BIGINT                                                              NOT NULL,
    attempt_number      INT                                                                 NOT NULL DEFAULT 1,
    failure_reason      ENUM('CUSTOMER_ABSENT','ADDRESS_NOT_FOUND','REFUSED','DAMAGED','OTHER') NOT NULL,
    notes               TEXT                                                                NULL     DEFAULT NULL,
    attempted_at        DATETIME                                                            NOT NULL,
    CONSTRAINT pk_delivery_attempts     PRIMARY KEY (id),
    CONSTRAINT fk_attempts_shipments    FOREIGN KEY (shipment_id) REFERENCES shipments (id) ON DELETE CASCADE,
    CONSTRAINT fk_attempts_users        FOREIGN KEY (agent_id)    REFERENCES users (id)     ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_attempts_shipment_id ON delivery_attempts (shipment_id);
CREATE INDEX idx_attempts_agent_id    ON delivery_attempts (agent_id);

-- -----------------------------------------------------------------------------
-- 6. refresh_tokens
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    token_hash      VARCHAR(64)  NOT NULL,
    expires_at      DATETIME     NOT NULL,
    created_at      DATETIME     NOT NULL,
    CONSTRAINT pk_refresh_tokens    PRIMARY KEY (id),
    CONSTRAINT uq_tokens_hash       UNIQUE (token_hash),
    CONSTRAINT fk_tokens_users      FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_tokens_user_id    ON refresh_tokens (user_id);
CREATE INDEX idx_tokens_expires_at ON refresh_tokens (expires_at);
