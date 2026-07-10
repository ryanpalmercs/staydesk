CREATE TABLE IF NOT EXISTS room_types
(
    id                INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name              VARCHAR     NOT NULL UNIQUE,
    available_count   INT         NOT NULL DEFAULT 0,
    unavailable_count INT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO room_types (name, available_count, unavailable_count)
SELECT type,
       COUNT(*) FILTER (WHERE status != 'MAINTENANCE'),
       COUNT(*) FILTER (WHERE status = 'MAINTENANCE')
FROM rooms
GROUP BY type
ON CONFLICT (name) DO NOTHING;

ALTER TABLE rooms
    ADD COLUMN IF NOT EXISTS room_type_id INT REFERENCES room_types (id);

UPDATE rooms
SET room_type_id = room_types.id
FROM room_types
WHERE rooms.type = room_types.name
  AND rooms.room_type_id IS NULL;

ALTER TABLE rooms
    ALTER COLUMN room_type_id SET NOT NULL,
    DROP COLUMN IF EXISTS type;

ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS room_type_id INT REFERENCES room_types (id),
    ALTER COLUMN room_id DROP NOT NULL;

UPDATE reservations r
SET room_type_id = ro.room_type_id
FROM rooms ro
WHERE r.room_id = ro.id
  AND r.room_type_id IS NULL;

ALTER TABLE reservations
    ALTER COLUMN room_type_id SET NOT NULL;
