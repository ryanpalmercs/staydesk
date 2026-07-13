CREATE TABLE IF NOT EXISTS pos_devices
(
    id            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id     VARCHAR     NOT NULL UNIQUE,
    friendly_name VARCHAR(12) NOT NULL,
    location      VARCHAR(16),
    paired_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER pos_devices_updated_at
    BEFORE UPDATE
    ON pos_devices
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

ALTER TABLE folio_payments
    ADD COLUMN provider VARCHAR NOT NULL DEFAULT 'authorizenet';