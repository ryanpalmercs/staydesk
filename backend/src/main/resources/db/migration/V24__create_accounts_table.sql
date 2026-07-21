CREATE TABLE IF NOT EXISTS accounts
(
    id           UUID PRIMARY KEY,
    kind         VARCHAR     NOT NULL,
    display_name VARCHAR,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO accounts (id, kind, display_name, active, created_at, updated_at)
SELECT id, 'EMPLOYEE', NULL, active, created_at, updated_at
FROM employees
ON CONFLICT DO NOTHING;

ALTER TABLE employees
    DROP CONSTRAINT IF EXISTS fk_employees_account,
    ADD CONSTRAINT fk_employees_account FOREIGN KEY (id) REFERENCES accounts (id);

ALTER TABLE guests
    DROP CONSTRAINT IF EXISTS guests_flagged_by_fkey,
    ADD CONSTRAINT guests_flagged_by_fkey FOREIGN KEY (flagged_by) REFERENCES accounts (id);