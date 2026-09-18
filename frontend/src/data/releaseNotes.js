// Hand-maintained, newest first. `id` is a plain sequential integer bumped by 1 for each new
// entry - it doesn't need to match any deploy or version number, it's just how WhatsNewGate
// figures out which entries a given employee/account hasn't seen yet (last_seen_release_notes_id
// vs. this id). `version` is just a display label (the release-lane tag this shipped under, e.g.
// "v1.4.1") - it plays no role in the tracking logic, only `id` does. Add a new entry here when
// there's something worth telling staff about, bugfixes included so they know something they hit
// has actually been fixed; it doesn't need to happen on every deploy.
export const releaseNotes = [
    {
        id: 1,
        title: "What's New",
        version: 'v1.4.1',
        date: '2026-09-18',
        notes: [
            "You can now move a checked-in guest to a different room, or reassign a confirmed reservation's room, right from Edit Reservation.",
            'Multi-night rates now split evenly across the whole stay instead of losing pennies to rounding.'
        ]
    }
]
