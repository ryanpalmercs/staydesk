import { describe, expect, it } from 'vitest'
import { formatGuestName } from './guestName'

describe('formatGuestName', () => {
    it('returns an empty string for a missing guest', () => {
        expect(formatGuestName(null)).toBe('')
        expect(formatGuestName(undefined)).toBe('')
    })

    it('prefers the backend-computed name field, uppercased', () => {
        expect(formatGuestName({ name: 'Jane Doe', firstName: 'Jane', lastName: 'Doe' })).toBe('JANE DOE')
    })

    it('falls back to joining first/last name when name is absent', () => {
        expect(formatGuestName({ firstName: 'Jane', lastName: 'Doe' })).toBe('JANE DOE')
    })

    it('falls back to just first name when there is no last name', () => {
        expect(formatGuestName({ firstName: 'Cher' })).toBe('CHER')
    })
})
