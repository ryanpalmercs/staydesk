import { describe, expect, it } from 'vitest'
import { isDayBeforeScheduledRelease } from './releaseSchedule'

describe('isDayBeforeScheduledRelease', () => {
    it('is true the day before the first scheduled release', () => {
        expect(isDayBeforeScheduledRelease(new Date('2026-10-05T12:00:00'))).toBe(true)
    })

    it('is true the day before a later cadence date (14 days on)', () => {
        expect(isDayBeforeScheduledRelease(new Date('2026-10-19T12:00:00'))).toBe(true)
    })

    it('is false on the release day itself', () => {
        expect(isDayBeforeScheduledRelease(new Date('2026-10-06T12:00:00'))).toBe(false)
    })

    it('is false on a Reminder Tuesday\'s eve (off-cycle week)', () => {
        expect(isDayBeforeScheduledRelease(new Date('2026-10-12T12:00:00'))).toBe(false)
    })

    it('is false before the schedule started', () => {
        expect(isDayBeforeScheduledRelease(new Date('2026-09-01T12:00:00'))).toBe(false)
    })
})
