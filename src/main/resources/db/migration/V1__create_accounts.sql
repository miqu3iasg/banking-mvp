CREATE SEQUENCE IF NOT EXISTS account_number_seq START 1;

CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number  CHAR(8)         NOT NULL UNIQUE,
    type            VARCHAR(20)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    balance         NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    currency_code   CHAR(3)         NOT NULL DEFAULT 'BRL',
    holder_name     VARCHAR(255)    NOT NULL,
    document_number VARCHAR(18)     NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_account_type CHECK (type IN ('CHECKING', 'SAVINGS')),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED'))
);

CREATE INDEX idx_accounts_document ON accounts (document_number);
CREATE INDEX idx_accounts_status ON accounts (status);
