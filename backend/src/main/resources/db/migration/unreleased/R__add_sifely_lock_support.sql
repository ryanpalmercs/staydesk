CREATE TABLE IF NOT EXISTS sifely_connections
(
    id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    access_token VARCHAR     NOT NULL,
    client_id    VARCHAR     NOT NULL,
    connected_at timestamptz NOT NULL DEFAULT NOW()
);

ALTER TABLE rooms
    ADD COLUMN IF NOT EXISTS sifely_lock_id BIGINT;