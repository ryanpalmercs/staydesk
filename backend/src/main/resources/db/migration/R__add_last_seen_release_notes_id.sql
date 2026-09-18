-- Replaces the short-lived last_seen_app_version (raw deployed git SHA) with a small sequential
-- id matching frontend/src/data/releaseNotes.js entries. A raw SHA can't tell you which
-- hand-written release notes a user has seen, since most deploys don't add a new entry.
ALTER TABLE employees
    DROP COLUMN IF EXISTS last_seen_app_version,
    ADD COLUMN IF NOT EXISTS last_seen_release_notes_id INTEGER;

ALTER TABLE accounts
    DROP COLUMN IF EXISTS last_seen_app_version,
    ADD COLUMN IF NOT EXISTS last_seen_release_notes_id INTEGER;
