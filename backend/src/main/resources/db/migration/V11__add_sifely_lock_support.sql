CREATE TABLE IF NOT EXISTS sifely_connections
(
    id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_token VARCHAR     NOT NULL,
    client_id    VARCHAR     NOT NULL,
    connected_at timestamptz NOT NULL DEFAULT NOW()
);

ALTER TABLE rooms
    ADD COLUMN IF NOT EXISTS sifely_lock_id BIGINT;

CREATE TABLE IF NOT EXISTS lock_passcodes
(
    id              INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id  INT REFERENCES reservations (id) NOT NULL,
    lock_id         BIGINT                           NOT NULL,
    keyboard_pwd_id BIGINT                           NOT NULL,
    passcode        VARCHAR                          NOT NULL,
    start_date      TIMESTAMPTZ                      NOT NULL,
    end_date        TIMESTAMPTZ                      NOT NULL,
    status          VARCHAR                          NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ                      NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ
);