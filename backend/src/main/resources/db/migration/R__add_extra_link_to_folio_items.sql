ALTER TABLE folio_items
    ADD COLUMN IF NOT EXISTS extra_id INT REFERENCES extras (id),
    ADD COLUMN IF NOT EXISTS quantity INT;
