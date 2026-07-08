-- Issue #70: guest/employee PII encryption at rest.
-- Step 2 of 2. Only safe to run after PiiBackfillRunner has populated email_hash on all
-- existing rows (see V14__add_guest_employee_pii_encryption.sql).

ALTER TABLE guests
    ALTER COLUMN email_hash SET NOT NULL,
    ADD CONSTRAINT guests_email_hash_key UNIQUE (email_hash);

ALTER TABLE employees
    ALTER COLUMN email_hash SET NOT NULL,
    ADD CONSTRAINT employees_email_hash_key UNIQUE (email_hash);
