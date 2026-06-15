CREATE TABLE folios (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    stripe_payment_intent_id VARCHAR(255) UNIQUE,
    paid_at TIMESTAMPTZ,
    paid_amount_cents BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_folios_status CHECK (status IN ('OPEN', 'PAID')),
    CONSTRAINT chk_folios_paid_amount_non_negative CHECK (
        paid_amount_cents IS NULL OR paid_amount_cents >= 0
    )
);
