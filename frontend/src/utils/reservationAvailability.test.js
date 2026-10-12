import { describe, expect, it } from 'vitest'
import { isExtendBlocked } from './reservationAvailability'

const BASE = { id: 1, roomId: 24, checkInDate: '2026-10-12', checkOutDate: '2026-10-19', status: 'CHECKED_IN' }

describe('isExtendBlocked', () => {
    it('is false with no reservation selected', () => {
        expect(isExtendBlocked(null, [BASE])).toBe(false)
    })

    it('is false for an unassigned (deferred) room', () => {
        expect(isExtendBlocked({ ...BASE, roomId: null }, [BASE])).toBe(false)
    })

    it('is false when no other reservation is on the same room', () => {
        const other = { id: 2, roomId: 99, checkInDate: '2026-10-19', checkOutDate: '2026-10-21', status: 'CONFIRMED' }
        expect(isExtendBlocked(BASE, [BASE, other])).toBe(false)
    })

    it('is true when another active reservation starts on this room on the check-out date (same-day turnover)', () => {
        const other = { id: 2, roomId: 24, checkInDate: '2026-10-19', checkOutDate: '2026-10-21', status: 'CONFIRMED' }
        expect(isExtendBlocked(BASE, [BASE, other])).toBe(true)
    })

    it('is false when the other reservation on this room starts after the check-out date', () => {
        const other = { id: 2, roomId: 24, checkInDate: '2026-10-20', checkOutDate: '2026-10-22', status: 'CONFIRMED' }
        expect(isExtendBlocked(BASE, [BASE, other])).toBe(false)
    })

    it('ignores cancelled, checked-out, and no-show reservations on the same room', () => {
        const reservations = [
            BASE,
            { id: 2, roomId: 24, checkInDate: '2026-10-19', checkOutDate: '2026-10-21', status: 'CANCELLED' },
            { id: 3, roomId: 24, checkInDate: '2026-10-19', checkOutDate: '2026-10-21', status: 'CHECKED_OUT' },
            { id: 4, roomId: 24, checkInDate: '2026-10-19', checkOutDate: '2026-10-21', status: 'NO_SHOW' }
        ]
        expect(isExtendBlocked(BASE, reservations)).toBe(false)
    })

    it('ignores the reservation itself', () => {
        expect(isExtendBlocked(BASE, [BASE])).toBe(false)
    })
})
