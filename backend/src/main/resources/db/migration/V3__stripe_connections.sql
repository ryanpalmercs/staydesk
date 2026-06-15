CREATE TABLE stripe_connections (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stripe_account_id VARCHAR NOT NULL,
    access_token VARCHAR NOT NULL,
    refresh_token VARCHAR,
    connected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT one_row CHECK (id = 1)
);