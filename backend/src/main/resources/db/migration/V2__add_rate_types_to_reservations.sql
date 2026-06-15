ALTER TABLE reservations
    ADD COLUMN rate_type   VARCHAR NOT NULL DEFAULT 'NIGHTLY',
    ADD COLUMN guest_count INT     NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS rates
(
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rate_type   VARCHAR       NOT NULL,
    guest_count INT           NOT NULL,
    amount      DECIMAL(6, 2) NOT NULL,
    UNIQUE (rate_type, guest_count)
);

INSERT INTO rates (rate_type, guest_count, amount)
VALUES ('NIGHTLY', 1, 64.17),
       ('NIGHTLY', 2, 75),
       ('WEEKLY_5', 1, 230.5),
       ('WEEKLY_7', 1, 322.7),
       ('WEEKLY_5', 2, 246.8),
       ('WEEKLY_7', 2, 345),
       ('WEEKLY_5', 3, 299),
       ('WEEKLY_7', 3, 418.6);

