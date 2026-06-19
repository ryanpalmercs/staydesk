DROP TABLE time_entries;
DROP TABLE employees;

CREATE TABLE employee_types
(
    id        INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name      VARCHAR NOT NULL UNIQUE,
    auth_role VARCHAR NOT NULL DEFAULT 'EMPLOYEE',
    active    BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO employee_types (name, auth_role)
VALUES ('Administrator', 'ADMIN'),
       ('Manager', 'MANAGER'),
       ('Front Desk', 'FRONT_DESK'),
       ('Housekeeping', 'HOUSEKEEPING'),
       ('General Employee', 'EMPLOYEE');

CREATE TABLE employees
(
    id               UUID PRIMARY KEY,
    first_name       VARCHAR       NOT NULL,
    last_name        VARCHAR       NOT NULL,
    email            VARCHAR       NOT NULL UNIQUE,
    username         VARCHAR       NOT NULL UNIQUE,
    employee_type_id INT           NOT NULL REFERENCES employee_types (id),
    pay_rate         DECIMAL(5, 2) NOT NULL,
    hire_date        DATE          NOT NULL,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE time_entries
(
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id UUID          NOT NULL REFERENCES employees (id),
    clock_in    TIMESTAMPTZ   NOT NULL,
    clock_out   TIMESTAMPTZ   NOT NULL,
    date        DATE          NOT NULL,
    hours       DECIMAL(4, 2) NOT NULL,
    notes       VARCHAR,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TRIGGER employees_updated_at
    BEFORE UPDATE
    ON employees
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER time_entries_updated_at
    BEFORE UPDATE
    ON time_entries
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();