CREATE TABLE boletos (
    id                   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id           UUID           NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    payer_name           VARCHAR(200)   NOT NULL,
    payer_document       VARCHAR(14)    NOT NULL,
    payer_street         VARCHAR(200)   NOT NULL,
    payer_number         VARCHAR(20)    NOT NULL,
    payer_neighborhood   VARCHAR(100)   NOT NULL,
    payer_zipcode        CHAR(8)        NOT NULL,
    payer_city           VARCHAR(100)   NOT NULL,
    payer_state          CHAR(2)        NOT NULL,
    amount               NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    due_date             DATE           NOT NULL,
    description          VARCHAR(500)   NOT NULL,
    provider_charge_id   BIGINT         UNIQUE,
    barcode              TEXT,
    billet_link          TEXT,
    pdf_url              TEXT,
    status               VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    paid_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT chk_boleto_status CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_boleto_payer_state CHECK (char_length(payer_state) = 2)
);

CREATE INDEX idx_boletos_account_date
    ON boletos (account_id, created_at DESC);

CREATE UNIQUE INDEX idx_boletos_provider_charge_id
    ON boletos (provider_charge_id)
    WHERE provider_charge_id IS NOT NULL;

-- Used by BoletoExpirationJob equivalent — mirrors V4 pattern for pix_charges
CREATE INDEX idx_boletos_expiration
    ON boletos (due_date)
    WHERE status = 'PENDING';

COMMENT ON TABLE boletos IS 'Boleto charges issued via Efí Bank Cobranças API';
COMMENT ON COLUMN boletos.provider_charge_id IS 'Numeric charge_id returned by Efí Bank after issuance. Null until issued';
COMMENT ON COLUMN boletos.barcode IS 'Linha digitável returned by Efí Bank. Null until issued';
COMMENT ON COLUMN boletos.payer_zipcode IS 'Digits only, no hyphen — 8 chars';
COMMENT ON COLUMN boletos.payer_state IS 'Brazilian state abbreviation (UF), e.g. SP, RJ';
COMMENT ON INDEX idx_boletos_expiration IS 'Partial index for efficient overdue PENDING boleto queries';
