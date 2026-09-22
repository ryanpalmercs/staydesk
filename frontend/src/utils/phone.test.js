import { describe, expect, it } from 'vitest'
import { formatPhone } from './phone'

describe('formatPhone', () => {
    it('returns raw digits when there are 3 or fewer', () => {
        expect(formatPhone('55')).toBe('55')
    })

    it('formats a partial number with an open prefix group once past 6 digits', () => {
        expect(formatPhone('555123')).toBe('(555) 123')
        expect(formatPhone('5551234')).toBe('(555) 123-4')
    })

    it('formats a complete 10-digit number', () => {
        expect(formatPhone('5551234567')).toBe('(555) 123-4567')
    })

    it('strips non-digit characters and truncates past 10 digits', () => {
        expect(formatPhone('(555) 123-456789')).toBe('(555) 123-4567')
    })
})
