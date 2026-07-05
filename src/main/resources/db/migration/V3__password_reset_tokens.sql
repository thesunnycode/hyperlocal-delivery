-- V3__password_reset_tokens.sql
-- Adds password_reset_tokens, backing the forgot-password / reset-password
-- flow (X2 in the design handoff). Mirrors refresh_tokens: only a SHA-256
-- hash of the token is stored, never the plaintext.

CREATE TABLE password_reset_tokens (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    token_hash      VARCHAR(64)  NOT NULL,
    expires_at      DATETIME     NOT NULL,
    used_at         DATETIME     NULL     DEFAULT NULL,
    created_at      DATETIME     NOT NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_reset_tokens_hash     UNIQUE (token_hash),
    CONSTRAINT fk_reset_tokens_users    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_reset_tokens_hash    ON password_reset_tokens (token_hash);
CREATE INDEX idx_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX idx_reset_tokens_expires ON password_reset_tokens (expires_at);
