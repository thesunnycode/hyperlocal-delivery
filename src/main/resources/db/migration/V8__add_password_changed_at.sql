-- V8: Add password_changed_at column for reset-token replay protection.
-- NULL means "password was never changed via reset" (initial registration state).
-- Only updated by the password-reset flow, never by profile edits.
ALTER TABLE users ADD COLUMN password_changed_at DATETIME(6) NULL AFTER deleted_at;
