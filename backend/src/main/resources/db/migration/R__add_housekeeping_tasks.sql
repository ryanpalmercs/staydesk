-- One row per turnover cycle, not one row per room - current status is derived from which
-- timestamp is set (inspected_at -> INSPECTED, else cleaned_at -> CLEAN, else DIRTY), never
-- stored as its own column, so status and timestamps can never disagree. A new row is inserted
-- automatically on guest checkout (ReservationService.checkOut); CLEAN/INSPECTED are set on the
-- latest row for a room via HousekeepingService.
CREATE TABLE IF NOT EXISTS housekeeping_tasks
(
    id                   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id              INT         NOT NULL REFERENCES rooms (id),
    assigned_employee_id UUID REFERENCES employees (id),
    marked_dirty_at      TIMESTAMPTZ,
    cleaned_at           TIMESTAMPTZ,
    inspected_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS housekeeping_tasks_room_id_idx ON housekeeping_tasks (room_id, created_at DESC);

DROP TRIGGER IF EXISTS housekeeping_tasks_updated_at ON housekeeping_tasks;

CREATE TRIGGER housekeeping_tasks_updated_at
    BEFORE UPDATE
    ON housekeeping_tasks
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
