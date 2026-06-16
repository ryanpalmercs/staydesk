CREATE TABLE extras
(
    id         INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR       NOT NULL,
    price      DECIMAL(8, 2) NOT NULL,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TRIGGER extras_updated_at
    BEFORE UPDATE
    ON extras
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
