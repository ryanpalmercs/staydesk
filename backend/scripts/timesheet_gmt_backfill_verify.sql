-- Issue #229: timesheet clock times displaying as GMT instead of CST/CDT.
--
-- Read-only. Run this FIRST, before backfill_timesheet_gmt.sql, and sanity-check the
-- output against a shift or two whose actual clock-in/out time you know, before
-- running the UPDATE.
--
-- Context: time_entries.clock_in/clock_out are TIMESTAMPTZ. Rows written by the old
-- clock-in/clock-out flow (LocalDateTime.now() under the JVM's UTC default on Render)
-- stored the *correct* absolute instant, but the frontend displayed it as if it were
-- already America/Chicago wall-clock time, so it showed ~5-6 hours later than reality.
--
-- This selects rows presumed to come from the automatic clock-in/clock-out buttons
-- (not the admin manual-entry form, which always submits times on-the-minute, i.e.
-- seconds = 0) and shows what clock_in/clock_out/date would become if corrected.
--
-- CAVEAT: this is a heuristic (sub-minute precision = auto-clocked), not a real
-- "source" column on the table. Eyeball the results before trusting them -- a manual
-- entry created by hitting the admin API directly with non-zero seconds would be
-- misidentified as auto-clocked.

SELECT
    id,
    employee_id,
    date                                                          AS date_stored,
    (clock_in AT TIME ZONE 'America/Chicago')::date                AS date_corrected,
    clock_in                                                      AS clock_in_stored,
    (clock_in AT TIME ZONE 'America/Chicago') AT TIME ZONE 'UTC'   AS clock_in_corrected,
    clock_out                                                     AS clock_out_stored,
    CASE WHEN clock_out IS NOT NULL
         THEN (clock_out AT TIME ZONE 'America/Chicago') AT TIME ZONE 'UTC'
         END                                                      AS clock_out_corrected
FROM time_entries
WHERE date_trunc('minute', clock_in) <> clock_in
  AND (clock_out IS NULL OR date_trunc('minute', clock_out) <> clock_out)
ORDER BY clock_in;

-- Also worth a look: rows this heuristic EXCLUDES (on-the-minute clock_in/out), so you
-- can confirm none of these are actually auto-clocked entries that happened to land on
-- an exact minute.
SELECT
    id, employee_id, date, clock_in, clock_out
FROM time_entries
WHERE date_trunc('minute', clock_in) = clock_in
   OR (clock_out IS NOT NULL AND date_trunc('minute', clock_out) = clock_out)
ORDER BY clock_in;
