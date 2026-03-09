-- Add status column to idempotency_keys.
-- Backfill existing rows as COMPLETED (they were written by the old code
-- which only inserted fully-completed records).
ALTER TABLE idempotency_keys
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';

-- Make response_body nullable to allow PENDING placeholder inserts.
ALTER TABLE idempotency_keys
    ALTER COLUMN response_body DROP NOT NULL;
