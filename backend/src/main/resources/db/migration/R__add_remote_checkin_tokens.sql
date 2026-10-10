CREATE TABLE IF NOT EXISTS remote_checkin_tokens
(
    id             SERIAL PRIMARY KEY,
    reservation_id INT         NOT NULL REFERENCES reservations (id),
    token_hash     CHAR(64)    NOT NULL UNIQUE,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_remote_checkin_tokens_reservation_id ON remote_checkin_tokens (reservation_id);

INSERT INTO property_settings (name, value)
VALUES ('front_desk_notification_email', 'martinhousemotel@gmail.com'),
       ('email_front_desk_remote_checkin_subject', 'Remote check-in completed - Room {{roomNumber}}'),
       ('email_front_desk_remote_checkin_body',
        '<p>{{guestFirstName}} {{guestLastName}} completed remote check-in for Room {{roomNumber}}.</p>' ||
        '<p>Confirmation #{{confirmationNumber}}</p>')
ON CONFLICT (name) DO NOTHING;
