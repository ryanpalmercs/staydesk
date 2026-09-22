import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import StatusBadge from './StatusBadge'

describe('StatusBadge', () => {
    it('renders the mapped label and styling for a known status', () => {
        render(<StatusBadge status="CHECKED_IN" />)
        const badge = screen.getByText('Checked In')
        expect(badge.className).toContain('bg-green-100')
    })

    it('falls back to the raw status text and a default style for an unmapped status', () => {
        render(<StatusBadge status="SOME_NEW_STATUS" />)
        const badge = screen.getByText('SOME_NEW_STATUS')
        expect(badge.className).toContain('bg-tan')
    })
})
