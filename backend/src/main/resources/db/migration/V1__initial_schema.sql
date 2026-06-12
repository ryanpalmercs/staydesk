CREATE TABLE rooms
(
    id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_number  INT           NOT NULL,
    type         VARCHAR       NOT NULL,
    nightly_rate DECIMAL(5, 2) NOT NULL,
    status       VARCHAR       NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE guests
(
    id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name   VARCHAR        NOT NULL,
    last_name    VARCHAR        NOT NULL,
    email        VARCHAR UNIQUE NOT NULL,
    phone_number VARCHAR(10)    NOT NULL
        CONSTRAINT check_only_digits
            CHECK (phone_number ~ '^[0-9]+$'
                AND LENGTH(phone_number) = 10
                ),
    created_at   TIMESTAMPTZ    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL
);

CREATE TABLE reservations
(
    id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guest_id       INT REFERENCES guests (id) NOT NULL,
    room_id        INT REFERENCES rooms (id)  NOT NULL,
    check_in_date  DATE                       NOT NULL,
    check_out_date DATE                       NOT NULL,
    status         VARCHAR                    NOT NULL,
    checked_in_at  TIMESTAMPTZ,
    checked_out_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ                NOT NULL,
    updated_at     TIMESTAMPTZ                NOT NULL
);

CREATE TABLE folios
(
    id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id INT REFERENCES reservations (id) NOT NULL,
    status         VARCHAR                          NOT NULL,
    total          DECIMAL(8, 2)                    NOT NULL,
    created_at     TIMESTAMPTZ                      NOT NULL,
    updated_at     TIMESTAMPTZ                      NOT NULL
);

CREATE TABLE folio_items
(
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio_id    INT REFERENCES folios (id) NOT NULL,
    description VARCHAR                    NOT NULL,
    amount      DECIMAL(8, 2)              NOT NULL,
    type        VARCHAR                    NOT NULL,
    created_at  TIMESTAMPTZ                NOT NULL,
    updated_at  TIMESTAMPTZ                NOT NULL
);

CREATE TABLE employees
(
    id         INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name VARCHAR       NOT NULL,
    last_name  VARCHAR       NOT NULL,
    role       VARCHAR       NOT NULL,
    pay_rate   DECIMAL(5, 2) NOT NULL,
    hire_date  DATE          NOT NULL,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL
);

CREATE TABLE time_entries
(
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id INT REFERENCES employees (id) NOT NULL,
    clock_in    TIMESTAMPTZ                   NOT NULL,
    clock_out   TIMESTAMPTZ                   NOT NULL,
    date        DATE                          NOT NULL,
    hours       DECIMAL(4, 2)                 NOT NULL,
    notes       VARCHAR,
    created_at  TIMESTAMPTZ                   NOT NULL,
    updated_at  TIMESTAMPTZ                   NOT NULL
);

CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER rooms_updated_at
    BEFORE UPDATE
    ON rooms
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();