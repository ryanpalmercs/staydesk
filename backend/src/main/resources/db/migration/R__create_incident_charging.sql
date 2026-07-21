CREATE TABLE IF NOT EXISTS reusable_payment_credentials
(
    id                   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio_id             INT REFERENCES folios (id)       NOT NULL,
    reservation_id       INT REFERENCES reservations (id) NOT NULL,
    provider             VARCHAR                          NOT NULL,
    provider_customer_id VARCHAR,
    provider_token       VARCHAR                          NOT NULL,
    card_last4           VARCHAR(4),
    revoked              BOOLEAN                          NOT NULL DEFAULT FALSE,
    revoked_at           TIMESTAMPTZ,
    expires_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ                      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ                      NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS reusable_payment_credentials_active_folio_idx
    ON reusable_payment_credentials (folio_id) WHERE NOT revoked;

CREATE OR REPLACE TRIGGER reusable_payment_credentials_updated_at
    BEFORE UPDATE
    ON reusable_payment_credentials
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS incident_charge_requests
(
    id                             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio_id                       INT REFERENCES folios (id)                       NOT NULL,
    reusable_payment_credential_id INT REFERENCES reusable_payment_credentials (id) NOT NULL,
    amount                         DECIMAL(8, 2)                                    NOT NULL CHECK (amount > 0),
    reason                         VARCHAR                                          NOT NULL,
    status                         VARCHAR                                          NOT NULL DEFAULT 'PENDING',
    requested_by                   UUID REFERENCES employees (id)                   NOT NULL,
    requested_at                   TIMESTAMPTZ                                      NOT NULL DEFAULT NOW(),
    approved_by                    UUID REFERENCES employees (id),
    approved_at                    TIMESTAMPTZ,
    rejection_reason               VARCHAR,
    folio_payment_id               INT REFERENCES folio_payments (id),
    failure_reason                 VARCHAR,
    created_at                     TIMESTAMPTZ                                      NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ                                      NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE TRIGGER incident_charge_requests_updated_at
    BEFORE UPDATE
    ON incident_charge_requests
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();