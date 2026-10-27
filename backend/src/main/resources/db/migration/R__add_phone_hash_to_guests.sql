ALTER TABLE guests
    ADD COLUMN IF NOT EXISTS phone_hash CHAR(64);

CREATE INDEX IF NOT EXISTS idx_guests_phone_hash ON guests (phone_hash);
