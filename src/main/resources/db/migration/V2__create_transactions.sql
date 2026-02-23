CREATE TABLE transactions (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id             UUID         NOT NULL REFERENCES accounts(id),
    counterpart_account_id UUID,
    type                   VARCHAR(20)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',
    amount                 NUMERIC(19, 4) NOT NULL,
    currency_code          CHAR(3)      NOT NULL DEFAULT 'BRL',
    description            VARCHAR(500),
    idempotency_key        VARCHAR(255) NOT NULL UNIQUE,
    reference_id           VARCHAR(255),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_amount_positive      CHECK (amount > 0),
    CONSTRAINT chk_transaction_type     CHECK (type   IN ('DEBIT', 'CREDIT', 'TRANSFER', 'PIX', 'BOLETO')),
    CONSTRAINT chk_transaction_status   CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED'))
);

CREATE INDEX idx_transactions_account_created ON transactions (account_id, created_at DESC);
CREATE UNIQUE INDEX idx_transactions_idempotency ON transactions (idempotency_key);
CREATE INDEX idx_transactions_reference ON transactions (reference_id) WHERE reference_id IS NOT NULL;


CREATE TABLE idempotency_keys (
    idempotency_key  VARCHAR(100) PRIMARY KEY,
    response_body    TEXT         NOT NULL,
    operation_type   VARCHAR(50)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_keys (expires_at);
