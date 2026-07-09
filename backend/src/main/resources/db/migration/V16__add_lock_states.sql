CREATE TABLE IF NOT EXISTS lock_states
(
    lock_id        BIGINT PRIMARY KEY,
    state          VARCHAR NOT NULL,
    battery_level  INT,
    last_event_at  TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO property_settings (name, value) VALUES
                                                ('payment_provider', 'stripe'),
                                                ('lock_provider', 'sifely')
ON CONFLICT (name) DO NOTHING;

