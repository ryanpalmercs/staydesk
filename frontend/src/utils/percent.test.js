import { describe, expect, it } from 'vitest'
import { displayPercent, formatPercent, parsePercent } from './percent'

describe('parsePercent', () => {
    it('converts a whole-number percent string to its decimal fraction string', () => {
        expect(parsePercent('8.73')).toBe('0.0873')
        expect(parsePercent('100')).toBe('1')
    })

    it('returns an empty string for non-numeric input', () => {
        expect(parsePercent('abc')).toBe('')
    })
})

describe('formatPercent', () => {
    it('converts a decimal fraction to a whole-number percent string with two decimals', () => {
        expect(formatPercent('0.0873')).toBe('8.73')
        expect(formatPercent(1)).toBe('100.00')
    })

    it('returns an empty string for non-numeric input', () => {
        expect(formatPercent('abc')).toBe('')
    })
})

describe('displayPercent', () => {
    it('formats a whole-number percent value with a trailing % sign', () => {
        expect(displayPercent('8.73')).toBe('8.73%')
        expect(displayPercent(100)).toBe('100.00%')
    })

    it('returns an empty string for non-numeric input', () => {
        expect(displayPercent('abc')).toBe('')
    })
})
