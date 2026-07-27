-- V5__add_otp_tables.sql
-- Adds OTP verification tables for the email-based OTP flow used in
-- registration and password reset. Stores only SHA-256 hashes of codes.

CREATE TABLE otp_records (
    id              BIGINT                                          NOT NULL AUTO_INCREMENT,
    email           VARCHAR(255)                                    NOT NULL,
    purpose         ENUM('REGISTRATION','PASSWORD_RESET')           NOT NULL,
    code_hash       VARCHAR(64)                                     NOT NULL,
    status          ENUM('ACTIVE','VERIFIED','INVALIDATED')         NOT NULL DEFAULT 'ACTIVE',
    attempt_count   INT                                             NOT NULL DEFAULT 0,
    expires_at      DATETIME                                        NOT NULL,
    created_at      DATETIME                                        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_otp_records PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_otp_email_purpose_status ON otp_records (email, purpose, status);

CREATE TABLE pending_registrations (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    email           VARCHAR(255)  NOT NULL,
    payload_json    TEXT          NOT NULL,
    expires_at      DATETIME      NOT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_pending_registrations  PRIMARY KEY (id),
    CONSTRAINT uq_pending_reg_email      UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
