-- inventory_items is the catalog (one row per trackable item). inventory_transactions is an
-- append-only ledger (RESTOCK/USAGE rows); current stock is computed as
-- SUM(RESTOCK quantities) - SUM(USAGE quantities), never stored as its own column, so it can
-- never drift out of sync with the ledger (same convention as housekeeping_tasks).
CREATE TABLE IF NOT EXISTS inventory_items
(
    id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                VARCHAR     NOT NULL,
    category            VARCHAR     NOT NULL,
    unit                VARCHAR     NOT NULL,
    low_stock_threshold INT         NOT NULL,
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DROP TRIGGER IF EXISTS inventory_items_updated_at ON inventory_items;

CREATE TRIGGER inventory_items_updated_at
    BEFORE UPDATE
    ON inventory_items
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS inventory_transactions
(
    id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id      INT         NOT NULL REFERENCES inventory_items (id),
    type         VARCHAR     NOT NULL,
    quantity     INT         NOT NULL,
    room_id      INT REFERENCES rooms (id),
    performed_by UUID REFERENCES employees (id),
    note         VARCHAR,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS inventory_transactions_item_id_idx ON inventory_transactions (item_id, created_at DESC);
