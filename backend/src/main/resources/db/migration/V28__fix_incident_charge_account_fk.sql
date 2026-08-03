ALTER TABLE incident_charge_requests
    DROP CONSTRAINT IF EXISTS incident_charge_requests_requested_by_fkey,
    ADD CONSTRAINT incident_charge_requests_requested_by_fkey FOREIGN KEY (requested_by) REFERENCES accounts (id);

ALTER TABLE incident_charge_requests
    DROP CONSTRAINT IF EXISTS incident_charge_requests_approved_by_fkey,
    ADD CONSTRAINT incident_charge_requests_approved_by_fkey FOREIGN KEY (approved_by) REFERENCES accounts (id);
