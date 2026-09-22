ALTER TABLE terminal_transactions
    ADD COLUMN IF NOT EXISTS reference_no VARCHAR,
    ADD COLUMN IF NOT EXISTS authorization_no VARCHAR,
    ADD COLUMN IF NOT EXISTS card_last4 VARCHAR(4),
    ADD COLUMN IF NOT EXISTS host_response_text VARCHAR;

CREATE INDEX IF NOT EXISTS idx_terminal_transactions_reference_no ON terminal_transactions (reference_no);

CREATE INDEX IF NOT EXISTS idx_terminal_transactions_folio_payment_id ON terminal_transactions (folio_payment_id);

CREATE INDEX IF NOT EXISTS idx_terminal_transactions_created_at ON terminal_transactions (created_at);
