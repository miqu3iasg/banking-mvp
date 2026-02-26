CREATE TABLE outbox_events (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_type      VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    processed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_outbox_events         PRIMARY KEY (id),
    CONSTRAINT chk_outbox_status        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    CONSTRAINT chk_outbox_attempts      CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_status_created
    ON outbox_events (status, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_aggregate
    ON outbox_events (aggregate_id);

CREATE INDEX idx_outbox_failed_type
    ON outbox_events (event_type, created_at)
    WHERE status = 'FAILED';
