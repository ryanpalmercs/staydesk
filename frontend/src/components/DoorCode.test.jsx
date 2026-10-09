import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DoorCode from './DoorCode'

vi.mock('../api/lockPasscodeApi', () => ({
    getActiveLockPasscode: vi.fn()
}))

import { getActiveLockPasscode } from '../api/lockPasscodeApi'

describe('DoorCode', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('shows a loading state before the request resolves', () => {
        getActiveLockPasscode.mockReturnValue(new Promise(() => {}))
        render(<DoorCode reservationId={1} />)

        expect(screen.getByText('Loading...')).toBeInTheDocument()
    })

    it('shows the passcode once loaded', async () => {
        getActiveLockPasscode.mockResolvedValue({ data: { passcode: '4821', endDate: '2026-09-25T12:00:00Z' } })
        render(<DoorCode reservationId={1} />)

        expect(await screen.findByText('4821')).toBeInTheDocument()
    })

    it('shows a not-found message on a 404', async () => {
        getActiveLockPasscode.mockRejectedValue({ response: { status: 404 } })
        render(<DoorCode reservationId={1} />)

        expect(await screen.findByText('No active door code found for this reservation.')).toBeInTheDocument()
    })

    it('shows an error message on any other failure', async () => {
        getActiveLockPasscode.mockRejectedValue({ response: { status: 500 } })
        render(<DoorCode reservationId={1} />)

        expect(await screen.findByText('Failed to load the door code.')).toBeInTheDocument()
    })
})
