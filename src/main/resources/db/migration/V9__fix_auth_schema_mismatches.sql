-- V9: Fix critical schema-entity mismatches identified in security audit
-- Addresses: C-5 (mfa_secret size), C-4 (email_verification_tokens plaintext email),
--            H-8 (password_reset_tokens CHECK constraint), L-1 (audit_log user_agent column)

-- 1. Fix mfa_secret column: VARCHAR(64) -> VARCHAR(512)
-- AES-256-GCM encrypted output (IV + ciphertext + GCM tag, Base64-encoded) exceeds 64 chars
ALTER TABLE users ALTER COLUMN mfa_secret TYPE VARCHAR(512);

-- 2. Fix email_verification_tokens: drop plaintext email, add email_hash
-- The entity only uses emailHash; plaintext email was a privacy violation
ALTER TABLE email_verification_tokens
    ADD COLUMN email_hash VARCHAR(64);

-- Backfill email_hash from the users table for existing tokens
UPDATE email_verification_tokens evt
SET email_hash = u.email_hash
FROM users u
WHERE evt.user_id = u.id
  AND evt.email_hash IS NULL;

-- Now that we've backfilled, make it NOT NULL
ALTER TABLE email_verification_tokens
    ALTER COLUMN email_hash SET NOT NULL;

-- Drop the plaintext email column
ALTER TABLE email_verification_tokens
    DROP COLUMN IF EXISTS email;

-- Add index on email_hash for lookup
CREATE INDEX IF NOT EXISTS idx_email_verif_email_hash ON email_verification_tokens(email_hash);

-- 3. Fix password_reset_tokens CHECK constraint: add lower bound
-- Drop existing constraint (PostgreSQL auto-names it, so we find it dynamically)
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT conname INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'password_reset_tokens'::regclass
      AND contype = 'c'
      AND conname LIKE '%attempt_count%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE password_reset_tokens DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

-- Add corrected constraint with both bounds
ALTER TABLE password_reset_tokens
    ADD CONSTRAINT chk_attempt_count_range CHECK (attempt_count >= 0 AND attempt_count <= 10);

-- 4. Add user_agent_hash column to audit_log
-- The entity maps userAgentHash VARCHAR(64) but migration had user_agent VARCHAR(500)
ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS user_agent_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_audit_user_agent_hash ON audit_log(user_agent_hash);

-- 5. Ensure composite index exists on refresh_tokens for token theft detection
CREATE INDEX IF NOT EXISTS idx_refresh_token_family_revoked_expires
    ON refresh_tokens(family_id, revoked, expires_at);
