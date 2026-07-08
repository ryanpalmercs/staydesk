-- Issue #70: guest/employee PII encryption at rest.
-- Step 1 of 2. Adds email_hash as NULLABLE here so existing rows don't fail an immediate
-- NOT NULL check; run the PiiBackfillRunner (pii.backfill.enabled=true) to populate it and
-- re-encrypt first_name/last_name/email/phone_number/contact_info before applying step 2
-- (V15__tighten_pii_email_hash_constraints.sql), which adds NOT NULL + UNIQUE on email_hash.

ALTER TABLE guests
    DROP CONSTRAINT IF EXISTS check_only_digits,
    DROP CONSTRAINT IF EXISTS guests_email_key,
    ALTER COLUMN phone_number TYPE VARCHAR,
    ADD COLUMN IF NOT EXISTS email_hash CHAR(64);

ALTER TABLE employees
    DROP CONSTRAINT IF EXISTS employees_email_key,
    ADD COLUMN IF NOT EXISTS email_hash CHAR(64),
    ALTER COLUMN contact_info TYPE TEXT USING contact_info::text;
