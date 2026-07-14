ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS channel VARCHAR NOT NULL DEFAULT 'PHONE';

ALTER TABLE folios
    DROP CONSTRAINT folios_reservation_id_fkey,
    ADD CONSTRAINT folios_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE CASCADE;

ALTER TABLE folio_items
    DROP CONSTRAINT folio_items_folio_id_fkey,
    ADD CONSTRAINT folio_items_folio_id_fkey FOREIGN KEY (folio_id) REFERENCES folios (id) ON DELETE CASCADE;

ALTER TABLE folio_payments
    DROP CONSTRAINT folio_payments_folio_id_fkey,
    ADD CONSTRAINT folio_payments_folio_id_fkey FOREIGN KEY (folio_id) REFERENCES folios (id) ON DELETE CASCADE;

ALTER TABLE lock_passcodes
    DROP CONSTRAINT lock_passcodes_reservation_id_fkey,
    ADD CONSTRAINT lock_passcodes_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE CASCADE;