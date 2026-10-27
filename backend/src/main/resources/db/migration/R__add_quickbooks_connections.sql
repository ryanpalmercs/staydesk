CREATE TABLE IF NOT EXISTS quickbooks_connections
(
    id                       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    realm_id                 VARCHAR     NOT NULL,
    refresh_token            VARCHAR     NOT NULL,
    refresh_token_expires_at timestamptz NOT NULL,
    connected_at             timestamptz NOT NULL DEFAULT NOW()
);