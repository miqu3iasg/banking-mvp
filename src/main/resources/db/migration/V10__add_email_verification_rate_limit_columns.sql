-- V10: Add rate limiting columns to email_verification_tokens
-- Addresses: L-8 — resendCount and lastResentAt for data-layer enforcement

ALTER TABLE email_verification_tokens
    ADD COLUMN IF NOT EXISTS resend_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_resent_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_email_verif_last_resent ON email_verification_tokens(last_resent_at);
