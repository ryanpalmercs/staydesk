ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS last_seen_app_version TEXT;

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS last_seen_app_version TEXT;
