-- Seeds the local dev DB with a snapshot of production's room_types/rooms/rates.
-- Pulled from the production Supabase project on 2026-09-09.
--
-- Deliberately excludes property_settings (per Ryan: "don't touch property_settings itself" --
-- those are business values set once locally, not overwritten from prod).
--
-- Stale OCCUPIED rooms are reset to AVAILABLE here ("mark available") since production occupancy
-- reflects live guests, which isn't a useful starting state for local dev/testing. Genuine
-- MAINTENANCE rooms (room 32/37, "Under Construction") are left as-is.
--
-- Uses upsert (ON CONFLICT DO UPDATE), not TRUNCATE -- rooms/room_types are referenced by
-- reservations and folios via foreign keys, so a CASCADE truncate would wipe those too. This is
-- safe to re-run any time to refresh reference data without touching reservation history.
--
-- Prerequisite: run `./gradlew bootRun` once first so Flyway has applied every migration on this
-- branch (this seed assumes room_types.pet_friendly already exists).
--
-- Run against the local Docker Postgres (see docker-compose.yml for credentials):
--   PGPASSWORD=local_password psql -h localhost -p 5433 -U postgres -d staydesk_dev -f backend/scripts/seedlocaldevdata.sql

BEGIN;

INSERT INTO room_types (id, name, available_count, unavailable_count, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES
    (1, 'Single Queen', 6, 2, '2026-08-02 21:13:26.775981+00', '2026-08-02 21:13:26.775981+00'),
    (2, 'Single Full/Double Twin', 1, 0, '2026-08-02 21:13:26.775981+00', '2026-08-02 21:13:26.775981+00'),
    (3, 'Double Full', 3, 0, '2026-08-02 21:13:26.775981+00', '2026-08-02 21:13:26.775981+00'),
    (4, 'Single Full', 9, 1, '2026-08-02 21:13:26.775981+00', '2026-08-02 21:13:26.775981+00'),
    (5, 'Pet-Friendly Queen', 1, 0, '2026-08-02 21:18:56.579818+00', '2026-08-02 21:18:56.579818+00'),
    (6, 'Pet-Friendly Full', 1, 0, '2026-08-02 21:18:56.579818+00', '2026-08-02 21:18:56.579818+00')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    available_count = EXCLUDED.available_count,
    unavailable_count = EXCLUDED.unavailable_count,
    updated_at = EXCLUDED.updated_at;

SELECT setval(pg_get_serial_sequence('room_types', 'id'), (SELECT MAX(id) FROM room_types));

-- status column: every OCCUPIED row from production is rewritten to AVAILABLE below; MAINTENANCE
-- rows (11, 18) are left untouched.
INSERT INTO rooms (id, room_type_id, room_number, status, maintenance_note, sifely_lock_id, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 26, 'AVAILABLE', '', NULL, '2026-08-02 21:14:25.773574+00', '2026-09-02 20:14:42.178304+00'),
    (2, 1, 27, 'AVAILABLE', '', NULL, '2026-08-02 21:14:54.990956+00', '2026-09-02 15:53:10.287259+00'),
    (3, 1, 28, 'AVAILABLE', '', NULL, '2026-08-02 21:15:06.999413+00', '2026-09-09 14:02:37.999605+00'),
    (4, 2, 22, 'AVAILABLE', '', NULL, '2026-08-02 21:15:21.644175+00', '2026-09-02 20:19:34.152097+00'),
    (5, 3, 23, 'AVAILABLE', '', NULL, '2026-08-02 21:15:34.136391+00', '2026-09-02 20:20:59.115176+00'),
    (6, 4, 29, 'AVAILABLE', '', NULL, '2026-08-02 21:15:52.891589+00', '2026-09-02 20:22:51.167756+00'),
    (8, 4, 30, 'AVAILABLE', '', NULL, '2026-08-02 21:16:05.326353+00', '2026-09-09 14:12:01.40533+00'),
    (9, 4, 25, 'AVAILABLE', '', NULL, '2026-08-02 21:16:10.435118+00', '2026-09-09 14:12:26.221787+00'),
    (10, 4, 31, 'AVAILABLE', '', NULL, '2026-08-02 21:16:17.728024+00', '2026-08-27 21:27:12.356195+00'),
    (11, 1, 32, 'MAINTENANCE', 'Under Construction', NULL, '2026-08-02 21:16:26.817496+00', '2026-08-02 21:21:07.320038+00'),
    (12, 4, 33, 'AVAILABLE', '', NULL, '2026-08-02 21:16:31.697704+00', '2026-09-02 20:29:57.192157+00'),
    (13, 1, 34, 'AVAILABLE', 'Under Construction', NULL, '2026-08-02 21:16:37.271418+00', '2026-09-02 20:31:57.771919+00'),
    (14, 4, 21, 'AVAILABLE', '', NULL, '2026-08-02 21:16:43.035395+00', '2026-09-02 20:37:49.753037+00'),
    (15, 1, 35, 'AVAILABLE', 'Under Construction', NULL, '2026-08-02 21:16:49.45462+00', '2026-08-27 21:28:57.611951+00'),
    (16, 1, 36, 'AVAILABLE', '', NULL, '2026-08-02 21:16:58.445237+00', '2026-09-02 20:33:46.569228+00'),
    (17, 1, 12, 'AVAILABLE', '', NULL, '2026-08-02 21:17:04.502265+00', '2026-09-02 20:34:27.198155+00'),
    (18, 4, 37, 'MAINTENANCE', 'Under Construction', NULL, '2026-08-02 21:17:10.302988+00', '2026-08-02 21:20:57.594784+00'),
    (19, 3, 20, 'AVAILABLE', '', NULL, '2026-08-02 21:17:16.610552+00', '2026-09-02 20:35:14.550752+00'),
    (20, 4, 14, 'AVAILABLE', '', NULL, '2026-08-02 21:17:25.722832+00', '2026-09-02 20:35:51.940878+00'),
    (21, 4, 19, 'AVAILABLE', '', NULL, '2026-08-02 21:17:30.543422+00', '2026-09-02 20:38:07.600672+00'),
    (22, 4, 15, 'AVAILABLE', '', NULL, '2026-08-02 21:17:36.984094+00', '2026-09-02 20:37:02.626657+00'),
    (23, 3, 18, 'AVAILABLE', '', NULL, '2026-08-02 21:17:41.353312+00', '2026-08-27 21:27:42.023744+00'),
    (24, 6, 17, 'AVAILABLE', '', NULL, '2026-08-02 21:17:47.209269+00', '2026-09-02 15:47:05.007111+00'),
    (25, 5, 16, 'AVAILABLE', '', NULL, '2026-08-02 21:17:53.039984+00', '2026-09-02 15:49:28.076931+00')
ON CONFLICT (id) DO UPDATE SET
    room_type_id = EXCLUDED.room_type_id,
    room_number = EXCLUDED.room_number,
    status = EXCLUDED.status,
    maintenance_note = EXCLUDED.maintenance_note,
    sifely_lock_id = EXCLUDED.sifely_lock_id,
    updated_at = EXCLUDED.updated_at;

SELECT setval(pg_get_serial_sequence('rooms', 'id'), (SELECT MAX(id) FROM rooms));

INSERT INTO rates (id, rate_type, guest_count, amount, created_at, updated_at) OVERRIDING SYSTEM VALUE VALUES
    (1, 'NIGHTLY', 1, 84.17, '2026-07-10 12:52:49.503237+00', '2026-07-29 03:08:29.466607+00'),
    (2, 'NIGHTLY', 2, 95.00, '2026-07-10 12:52:49.503237+00', '2026-07-29 03:08:29.466607+00'),
    (3, 'WEEKLY_5', 1, 270.50, '2026-07-10 12:52:49.503237+00', '2026-07-29 03:08:29.466607+00'),
    (4, 'WEEKLY_7', 1, 362.70, '2026-07-10 12:52:49.503237+00', '2026-07-29 03:08:29.466607+00'),
    (5, 'WEEKLY_5', 2, 286.80, '2026-07-10 12:52:49.503237+00', '2026-07-29 03:08:29.466607+00'),
    (6, 'WEEKLY_7', 2, 385.00, '2026-07-10 12:52:49.503237+00', '2026-07-29 03:08:29.466607+00'),
    (7, 'WEEKLY_5', 3, 339.00, '2026-07-10 12:52:49.503237+00', '2026-07-29 03:08:29.466607+00'),
    (8, 'WEEKLY_7', 3, 458.60, '2026-07-10 12:52:49.503237+00', '2026-07-29 03:08:29.466607+00'),
    (11, 'NIGHTLY', 3, 105.83, '2026-07-29 03:08:29.466607+00', '2026-07-29 03:08:29.466607+00'),
    (12, 'NIGHTLY', 4, 116.66, '2026-07-29 03:08:29.466607+00', '2026-07-29 03:08:29.466607+00'),
    (19, 'WEEKLY_5', 4, 359.00, '2026-07-29 03:08:29.466607+00', '2026-07-29 03:08:29.466607+00'),
    (20, 'WEEKLY_7', 4, 478.60, '2026-07-29 03:08:29.466607+00', '2026-07-29 03:08:29.466607+00')
ON CONFLICT (id) DO UPDATE SET
    rate_type = EXCLUDED.rate_type,
    guest_count = EXCLUDED.guest_count,
    amount = EXCLUDED.amount,
    updated_at = EXCLUDED.updated_at;

SELECT setval(pg_get_serial_sequence('rates', 'id'), (SELECT MAX(id) FROM rates));

COMMIT;
