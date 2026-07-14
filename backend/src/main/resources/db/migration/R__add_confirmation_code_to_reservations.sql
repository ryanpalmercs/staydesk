ALTER TABLE reservations
    ADD COLUMN confirmation_code VARCHAR(6);

ALTER TABLE reservations
    ADD CONSTRAINT reservations_confirmation_code_key UNIQUE (confirmation_code);
