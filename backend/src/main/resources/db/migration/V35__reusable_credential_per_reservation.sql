-- A shared folio can now have multiple rooms checking in independently, each
-- capturing its own reusable payment credential from its own incidentals hold.
-- The old constraint allowed only one active credential per folio; scope it to
-- (folio_id, reservation_id) instead so each room can have its own.
DROP INDEX reusable_payment_credentials_active_folio_idx;

CREATE UNIQUE INDEX reusable_payment_credentials_active_folio_reservation_idx
    ON reusable_payment_credentials (folio_id, reservation_id) WHERE NOT revoked;