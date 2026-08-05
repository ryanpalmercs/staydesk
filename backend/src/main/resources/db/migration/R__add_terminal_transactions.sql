CREATE TABLE IF NOT EXISTS terminal_transactions
(
    id               INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flow_id          VARCHAR     NOT NULL UNIQUE,
    folio_payment_id INT REFERENCES folio_payments (id),
    operation        VARCHAR     NOT NULL,
    amount           NUMERIC(10, 2),
    status           VARCHAR     NOT NULL DEFAULT 'PENDING',
    request_payload  TEXT,
    response_payload TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER terminal_transactions_updated_at
    BEFORE UPDATE
    ON terminal_transactions
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();