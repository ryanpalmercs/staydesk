-- Nightly terminal settlement (batch-out) results. One row per attempt (COMPLETED or
-- FAILED - a failed batch is not retried automatically, see TerminalSettlementService).
-- Amounts/counts here are the terminal's own reported batch totals only; the folio-side
-- total is deliberately not duplicated here so the two can't drift - reconcile with a
-- query against folio_payments for the same date instead.
CREATE TABLE IF NOT EXISTS terminal_settlement_batches
(
    id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_date     DATE          NOT NULL,
    status         VARCHAR       NOT NULL,
    terminal_id    VARCHAR,
    merchant_id    VARCHAR,
    sale_count     INT,
    sale_amount    NUMERIC(10, 2),
    refund_count   INT,
    refund_amount  NUMERIC(10, 2),
    void_count     INT,
    void_amount    NUMERIC(10, 2),
    failure_reason TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS terminal_settlement_batches_batch_date_idx ON terminal_settlement_batches (batch_date);

DROP TRIGGER IF EXISTS terminal_settlement_batches_updated_at ON terminal_settlement_batches;

CREATE TRIGGER terminal_settlement_batches_updated_at
    BEFORE UPDATE
    ON terminal_settlement_batches
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
