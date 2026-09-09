-- start_date/end_date follow the same convention as reservations.check_in_date/check_out_date:
-- start_date is the first surged night, end_date is exclusive - the day pricing returns to normal.
CREATE TABLE IF NOT EXISTS rate_overrides
(
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rate_type   VARCHAR       NOT NULL,
    guest_count INT           NOT NULL,
    start_date  DATE          NOT NULL,
    end_date    DATE          NOT NULL,
    amount      DECIMAL(6, 2) NOT NULL,
    label       VARCHAR       NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

ALTER TABLE rate_overrides DROP CONSTRAINT IF EXISTS rate_overrides_check;
ALTER TABLE rate_overrides DROP CONSTRAINT IF EXISTS rate_overrides_end_after_start;
ALTER TABLE rate_overrides ADD CONSTRAINT rate_overrides_end_after_start CHECK (end_date > start_date);

CREATE INDEX IF NOT EXISTS rate_overrides_lookup_idx ON rate_overrides (rate_type, guest_count, start_date, end_date);

DROP TRIGGER IF EXISTS rate_overrides_updated_at ON rate_overrides;

CREATE TRIGGER rate_overrides_updated_at
    BEFORE UPDATE
    ON rate_overrides
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
