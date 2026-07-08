CREATE TABLE IF NOT EXISTS staff_lock_passcodes
(
    id              INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id     UUID REFERENCES employees (id) NOT NULL,
    lock_id         BIGINT                         NOT NULL,
    keyboard_pwd_id BIGINT                         NOT NULL,
    status          VARCHAR                        NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ                    NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ
);

ALTER TABLE lock_passcodes
    ALTER COLUMN keyboard_pwd_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS acknowledged BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS resolved_at  timestamptz;

INSERT INTO property_settings (name, value)
VALUES ('sms_door_code_resolved_template',
        'Room {{roomNumber}}: digital passcode is now active for the guest — retrieve the physical key.')
ON CONFLICT (name) DO NOTHING;

ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS door_access_enabled BOOLEAN NOT NULL DEFAULT FALSE;