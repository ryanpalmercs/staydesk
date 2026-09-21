ALTER TABLE reservations
    ADD COLUMN folio_id INT REFERENCES folios (id);

UPDATE reservations r
SET folio_id = f.id
FROM folios f
WHERE f.reservation_id = r.id;

ALTER TABLE folios
    DROP CONSTRAINT folios_reservation_id_fkey,
    DROP COLUMN reservation_id;

DO
$$
    DECLARE
        orphan       RECORD;
        new_folio_id INT;
    BEGIN
        FOR orphan IN SELECT id FROM reservations WHERE folio_id IS NULL
            LOOP
                INSERT INTO folios (status, total, paid_at, created_at, updated_at)
                VALUES ('CLOSED', 0, NULL, NOW(), NOW())
                RETURNING id INTO new_folio_id;

                UPDATE reservations SET folio_id = new_folio_id WHERE id = orphan.id;
            END LOOP;
    END
$$;

ALTER TABLE reservations
    ALTER COLUMN folio_id SET NOT NULL;

ALTER TABLE folio_payments
    ADD COLUMN reservation_id INT REFERENCES reservations (id);