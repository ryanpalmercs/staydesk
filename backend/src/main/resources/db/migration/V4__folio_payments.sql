ALTER TABLE folios
    ADD COLUMN paid_at TIMESTAMPTZ;

CREATE TABLE folio_payments
(
    id                       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio_id                 INT REFERENCES folios (id) NOT NULL,
    kind                     VARCHAR                     NOT NULL,
    stripe_payment_intent_id VARCHAR                     NOT NULL UNIQUE,
    status                   VARCHAR                     NOT NULL,
    authorized_amount        DECIMAL(8, 2)                NOT NULL,
    captured_amount          DECIMAL(8, 2),
    created_at               TIMESTAMPTZ                  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ                  NOT NULL DEFAULT NOW()
);

CREATE TRIGGER folio_payments_updated_at
    BEFORE UPDATE
    ON folio_payments
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();