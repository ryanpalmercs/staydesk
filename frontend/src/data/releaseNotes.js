// Hand-maintained, newest first. `id` is a plain sequential integer bumped by 1 for each new
// entry - it doesn't need to match any deploy or version number, it's just how WhatsNewGate
// figures out which entries a given employee/account hasn't seen yet (last_seen_release_notes_id
// vs. this id). `version` is an optional display label (the release-lane tag this shipped under,
// e.g. "v1.4.1") - it plays no role in the tracking logic, only `id` does. Leave it off and the
// newest entry falls back to whatever git tag the current build resolved (see vite.config.js);
// only set it by hand for an older entry, or if the auto-resolved tag isn't what you want shown.
// Add a new entry here when there's something worth telling staff about, bugfixes included so
// they know something they hit has actually been fixed; it doesn't need to happen on every
// deploy.
export const releaseNotes = [
    {
        id: 5,
        title: "What's New",
        version: 'v1.6.1',
        date: '2026-09-24',
        notes: [
            'The timesheet and payroll weeks now run Friday through Thursday instead of Monday through Sunday, and the week header shows the weekday names.'
        ]
    },
    {
        id: 4,
        title: "What's New",
        version: 'v1.6.0',
        date: '2026-09-22',
        notes: [
            'Reports (admin only) now include a Terminal Transactions section (counts and volume by status) - metrics are preliminary pending final sign-off.'
        ]
    },
    {
        id: 3,
        title: "What's New",
        version: 'v1.5.0',
        date: '2026-09-21',
        notes: [
            "Your name now shows in the dashboard heading and sidebar when you're logged in."
        ]
    },
    {
        id: 2,
        title: "What's New",
        version: 'v1.4.2',
        date: '2026-09-19',
        notes: [
            'Guests with a legacy weekly rate are now billed correctly across the whole stay, instead of being charged the full weekly rate every night.'
        ]
    },
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
