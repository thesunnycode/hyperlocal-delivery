-- V9__agent_invites.sql
-- Backs the agent invite flow.
--
-- An agent account is created by its owner with a random password that is
-- hashed immediately and shown to nobody, so before this table there was no
-- way for the agent to learn the account existed or to reach it. An invite is
-- a single-use, expiring link that lets that person set their own password.
--
-- Mirrors refresh_tokens and password_reset_tokens: only a SHA-256 hash of the
-- token is stored, never the plaintext. The plaintext exists once, in the
-- response to the owner and in the email, and is never persisted.
--
-- accepted_at doubles as the single-use marker: a non-NULL value means the
-- invite has been spent and must be refused, which is checked in the same
-- UPDATE that sets it so two racing requests cannot both succeed.

CREATE TABLE agent_invites (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    token_hash      VARCHAR(64)  NOT NULL,
    expires_at      DATETIME     NOT NULL,
    accepted_at     DATETIME     NULL     DEFAULT NULL,
    created_at      DATETIME     NOT NULL,
    CONSTRAINT pk_agent_invites       PRIMARY KEY (id),
    CONSTRAINT uq_agent_invites_hash  UNIQUE (token_hash),
    CONSTRAINT fk_agent_invites_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_agent_invites_hash    ON agent_invites (token_hash);
CREATE INDEX idx_agent_invites_user_id ON agent_invites (user_id);
CREATE INDEX idx_agent_invites_expires ON agent_invites (expires_at);
