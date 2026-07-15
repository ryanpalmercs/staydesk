ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS confirmation_code VARCHAR(6);

ALTER TABLE reservations
    DROP CONSTRAINT IF EXISTS reservations_confirmation_code_key,
    ADD CONSTRAINT reservations_confirmation_code_key UNIQUE (confirmation_code);
