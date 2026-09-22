import { describe, expect, it } from 'vitest'
import { displayPrice, formatPrice, sanitizePrice } from './price'

describe('sanitizePrice', () => {
    it('strips everything except digits and dots', () => {
        expect(sanitizePrice('$1,234.56abc')).toBe('1234.56')
    })

    it('treats null/undefined as an empty string', () => {
        expect(sanitizePrice(null)).toBe('')
        expect(sanitizePrice(undefined)).toBe('')
    })
})

describe('formatPrice', () => {
    it('formats a numeric string to two decimal places', () => {
        expect(formatPrice('64.1')).toBe('64.10')
        expect(formatPrice('64')).toBe('64.00')
    })

    it('returns an empty string for non-numeric input', () => {
        expect(formatPrice('abc')).toBe('')
    })
})

describe('displayPrice', () => {
    it('formats a number as a dollar-signed, comma-grouped string', () => {
        expect(displayPrice(1234.5)).toBe('$1,234.50')
        expect(displayPrice('64.17')).toBe('$64.17')
    })

    it('returns an empty string for non-numeric input', () => {
        expect(displayPrice('abc')).toBe('')
    })
})
