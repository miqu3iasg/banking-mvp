-- V11: Convert MFA backup codes from BCrypt to SHA-256 for O(1) validation
-- Addresses: M-3 — BCrypt O(n) loop is unacceptably slow
--
-- IMPORTANT: This migration INVALIDATES all existing backup codes.
-- BCrypt hashes cannot be converted to SHA-256 (both are one-way).
-- Users must regenerate their backup codes on next MFA use.
--
-- Strategy: Mark all unused codes as used (effectively invalidating them),
-- then resize the column for SHA-256 hex output (64 chars).

-- Invalidate all existing unused backup codes (users must regenerate)
UPDATE mfa_backup_codes SET used = TRUE, used_at = NOW()
WHERE used = FALSE;

-- Resize code_hash column from VARCHAR(120) (BCrypt) to VARCHAR(64) (SHA-256 hex)
ALTER TABLE mfa_backup_codes ALTER COLUMN code_hash TYPE VARCHAR(64);

-- Add partial index for O(1) lookup by code_hash on unused codes only
CREATE INDEX IF NOT EXISTS idx_mfa_backup_code_hash_unused
    ON mfa_backup_codes(code_hash) WHERE used = FALSE;
