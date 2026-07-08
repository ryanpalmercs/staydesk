CREATE TABLE IF NOT EXISTS property_settings
(
    name VARCHAR PRIMARY KEY,
    value VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER settings_updated_at
    BEFORE UPDATE
    ON property_settings
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

INSERT INTO property_settings (name, value)
VALUES ('incidentals_hold_amount', '75.00'),
        ('lodging_tax_rate', '0.0873');