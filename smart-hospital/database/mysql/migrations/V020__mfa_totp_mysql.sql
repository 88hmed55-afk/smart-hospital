-- V020: Time-based one-time password (TOTP) multi-factor authentication.
--
-- The legacy schema reserved user_accounts.mfa_enabled/mfa_secret but never used
-- them. This migration makes the secret column large enough for an AES-GCM
-- envelope, records when enrolment completed and the last accepted time step
-- (replay protection), and adds the two short-lived support tables:
--
--   user_mfa_challenges      one row per password-verified login awaiting a code;
--                            only the SHA-256 hash of the challenge token is stored.
--   user_mfa_recovery_codes  one-time codes, stored as SHA-256 hashes only.
--
-- Additive and non-destructive: existing accounts keep mfa_enrolled_at = NULL and
-- therefore behave exactly as before until they explicitly enrol.
USE hospital;

ALTER TABLE user_accounts
    MODIFY COLUMN mfa_secret VARCHAR(255) NULL,
    ADD COLUMN mfa_enrolled_at DATETIME(6) NULL AFTER mfa_secret,
    ADD COLUMN mfa_last_used_step BIGINT NULL AFTER mfa_enrolled_at;

CREATE TABLE user_mfa_challenges (
    id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id      CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ip_address   VARCHAR(45) NULL,
    user_agent   VARCHAR(400) NULL,
    attempts     INT NOT NULL DEFAULT 0,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at   DATETIME(6) NOT NULL,
    consumed_at  DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_mfa_challenge_token (token_hash),
    KEY ix_mfa_challenge_user (user_id, expires_at),
    CONSTRAINT fk_mfa_challenge_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE,
    CONSTRAINT ck_mfa_challenge_attempts CHECK (attempts >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_mfa_recovery_codes (
    id          CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_hash   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    used_at     DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_mfa_recovery_code (code_hash),
    KEY ix_mfa_recovery_user (user_id, used_at),
    CONSTRAINT fk_mfa_recovery_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
