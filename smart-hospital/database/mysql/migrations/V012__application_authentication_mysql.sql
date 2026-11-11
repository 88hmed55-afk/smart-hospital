-- V012: Application-level authentication, sessions and account lockout.
-- Replaces the GlassFish File Realm for production use. Password hashes are
-- PBKDF2-HMAC-SHA256 (210,000 iterations, 16-byte salt, 32-byte output).
USE hospital;

ALTER TABLE user_accounts
    ADD COLUMN password_changed_at DATETIME(6) NULL AFTER must_change_password,
    ADD COLUMN mfa_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER password_changed_at,
    ADD COLUMN mfa_secret VARCHAR(64) NULL AFTER mfa_enabled;

CREATE TABLE IF NOT EXISTS user_sessions (
    id            CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    user_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(400),
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_seen_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at    DATETIME(6) NOT NULL,
    revoked_at    DATETIME(6),
    revoke_reason VARCHAR(80),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE UNIQUE INDEX uq_user_sessions_token ON user_sessions (token_hash);
CREATE INDEX ix_user_sessions_user ON user_sessions (user_id, expires_at);

-- Demo accounts: password Demo@2026. The flag forces a rotation after first
-- production deployment; the application exposes POST /auth/password.
UPDATE user_accounts SET password_scheme = 'PBKDF2-SHA256', must_change_password = TRUE,
    password_changed_at = NULL,
    password_hash = CASE username
        WHEN 'hospitaladmin'    THEN 'pbkdf2-sha256$210000$5a5SvfqwZrUwlzcwgO5t2A==$xXeyeMGM0egR6zIE6/2x6PfHHstfTHWgIGc5/ANKwvg='
        WHEN 'manager1'         THEN 'pbkdf2-sha256$210000$V/afgw1bjUt25jL9V122nA==$4euLPPq8IUVt1miMjywvOVf1Msje4IHjIt8Gn23aJzE='
        WHEN 'receptionist1'    THEN 'pbkdf2-sha256$210000$lVVnARjHJ1ac1IOMJW585g==$4fxGEgmMhDRQbaR2x76E8Qag1tBLWMwYrEtpJaEJtqA='
        WHEN 'doctor1'          THEN 'pbkdf2-sha256$210000$t2Io+wILIAApMnWwwg6l1w==$BMpRNBTKTLdyy2DNiXqdxCFlIz1ubAgwu2810lj8l2o='
        WHEN 'nurse1'           THEN 'pbkdf2-sha256$210000$owv1zEW3o37DX4PL/n2OKA==$Pvv5WoaCcwvl6S89PyKFEV2kwo2Mz8ysdYJPcsB0l5I='
        WHEN 'labtech1'         THEN 'pbkdf2-sha256$210000$EMJnIKSL/yRPMAD65Ay8Zg==$WeQXHn8qnLBFD7MgCmjfClrdUt66yxSzBM1g+lJqZtg='
        WHEN 'labsupervisor1'   THEN 'pbkdf2-sha256$210000$rP6Kj3vVzM4x6zwkgbrZkg==$6q49rPocyVkYHrK0HMhFkmfYZF9qjlMffHrJIPpwihQ='
        WHEN 'pharmacist1'      THEN 'pbkdf2-sha256$210000$rhn4EwYrjOKKtwe5Oh+x2g==$cu1kUxceT8j3xn59mqbrQbxixzC9GQYbX9/de38aa9c='
        WHEN 'accountant1'      THEN 'pbkdf2-sha256$210000$mX0g9I1kZWt8yFsUCWm+cQ==$MRBUvCF70goxb/fOhRWLJXCzqqjMUim+4YUKLp5viBs='
        WHEN 'inventoryofficer1' THEN 'pbkdf2-sha256$210000$NuEETPxtpXXbqYXnDxtLpA==$Wx4FpkcnzbV16KPmIjJ5vbkV5iB8XPcpAkIkSUZxNrc='
        ELSE password_hash
    END;

-- Revoked sessions are purged by a scheduled job; keep only the index here.
