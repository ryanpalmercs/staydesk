ALTER TABLE reservations
    ADD COLUMN folio_id INT REFERENCES folios (id);

UPDATE reservations r
SET folio_id = f.id
FROM folios f
WHERE f.reservation_id = r.id;

ALTER TABLE reservations
    ALTER COLUMN folio_id SET NOT NULL;

ALTER TABLE folios
    DROP CONSTRAINT folios_reservation_id_fkey,
    DROP COLUMN reservation_id;

ALTER TABLE folio_payments
    ADD COLUMN reservation_id INT REFERENCES reservations (id);