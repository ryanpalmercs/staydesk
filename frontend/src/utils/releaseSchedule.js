import { addDays, differenceInCalendarDays } from 'date-fns'

// Mirrors the biweekly Release Tuesday cadence in .github/workflows/scheduled-release.yml (#389):
// first release 2026-10-06, then every 14 days. Doesn't cover emergency/off-cycle hotfix merges -
// those don't follow this schedule, so there's nothing here to predict them.
// Built with the (year, monthIndex, day) constructor rather than a date-only ISO string ('2026-10-06')
// on purpose - a bare ISO date string parses as UTC midnight, not local midnight, which can shift
// the calendar day by several hours (and so by a full day) depending on the browser's time zone.
const FIRST_RELEASE_TUESDAY = new Date(2026, 9, 6)
const CADENCE_DAYS = 14

export function isDayBeforeScheduledRelease(date = new Date()) {
    const tomorrow = addDays(date, 1)
    const daysSinceFirstRelease = differenceInCalendarDays(tomorrow, FIRST_RELEASE_TUESDAY)

    return daysSinceFirstRelease >= 0 && daysSinceFirstRelease % CADENCE_DAYS === 0
}
