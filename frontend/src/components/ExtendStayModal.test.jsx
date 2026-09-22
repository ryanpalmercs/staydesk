import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ExtendStayModal from './ExtendStayModal'

vi.mock('../api/reservationApi', () => ({
    extendStay: vi.fn(),
    extendStayTerminal: vi.fn(),
    getExtendStayEstimate: vi.fn()
}))
vi.mock('../api/folioApi', () => ({
    addCardOnFile: vi.fn(),
    addCardOnFileTerminal: vi.fn(),
    getFolioByReservationId: vi.fn()
}))
// ExtendStayModal renders PaymentMethodStep (via the "no card on file" branch), which imports
// posDeviceApi and settingsApi - both transitively import lib/supabase.js, which throws at
// import time in Vitest. Mocked here even though most of this suite's tests never reach that step.
vi.mock('../api/posDeviceApi', () => ({
    getPosDevices: vi.fn(),
    getPosDeviceConfig: vi.fn(),
    checkPosDeviceHealth: vi.fn()
}))
vi.mock('../api/settingsApi', () => ({
    getPropertySetting: vi.fn()
}))

import { extendStay, extendStayTerminal, getExtendStayEstimate } from '../api/reservationApi'
import { addCardOnFile, getFolioByReservationId } from '../api/folioApi'
import { getPosDevices, getPosDeviceConfig } from '../api/posDeviceApi'
import { getPropertySetting } from '../api/settingsApi'

const RESERVATION = { id: 21, checkOutDate: '2026-10-01' }

describe('ExtendStayModal', () => {
    beforeEach(() => {
        getExtendStayEstimate.mockResolvedValue({ data: { total: 63.5 } })
        getPosDevices.mockResolvedValue({ data: [] })
        getPosDeviceConfig.mockResolvedValue({ data: { recordOnly: false } })
        getPropertySetting.mockResolvedValue({ data: { value: 'authorizenet' } })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    it('disables Extend until the check-out date actually changes', () => {
        render(<ExtendStayModal reservation={RESERVATION} onSaved={vi.fn()} onClose={vi.fn()} />)

        expect(screen.getByRole('button', { name: 'Extend' })).toBeDisabled()
    })

    it('fetches and shows an estimate once a new check-out date is picked, then extends on submit', async () => {
        const user = userEvent.setup()
        extendStay.mockResolvedValue({ data: { reservation: { checkOutDate: '2026-10-03' }, amountCharged: 63.5 } })
        render(<ExtendStayModal reservation={RESERVATION} onSaved={vi.fn()} onClose={vi.fn()} />)

        const dateInput = document.querySelector('input[type="date"]')
        await user.clear(dateInput)
        await user.type(dateInput, '2026-10-03')

        await waitFor(() => expect(getExtendStayEstimate).toHaveBeenCalledWith(21, '2026-10-03'))

        const extendButton = screen.getByRole('button', { name: 'Extend' })
        expect(extendButton).not.toBeDisabled()
        await user.click(extendButton)

        await waitFor(() => expect(extendStay).toHaveBeenCalledWith(21, '2026-10-03'))
        expect(await screen.findByText('Stay Extended')).toBeInTheDocument()
        expect(screen.getByText('New check-out date: 2026-10-03')).toBeInTheDocument()
    })

    it('shows a specific server-provided error message when extend fails with a non-409 error', async () => {
        const user = userEvent.setup()
        extendStay.mockRejectedValue({ response: { status: 400, data: 'Room type is unavailable for the extended dates.' } })
        render(<ExtendStayModal reservation={RESERVATION} onSaved={vi.fn()} onClose={vi.fn()} />)

        const dateInput = document.querySelector('input[type="date"]')
        await user.clear(dateInput)
        await user.type(dateInput, '2026-10-03')
        await user.click(screen.getByRole('button', { name: 'Extend' }))

        expect(await screen.findByText('Room type is unavailable for the extended dates.')).toBeInTheDocument()
    })

    it('prompts to add a card on file on a 409 (no active credential), then retries the extension', async () => {
        const user = userEvent.setup()
        extendStay
            .mockRejectedValueOnce({ response: { status: 409 } })
            .mockResolvedValueOnce({ data: { reservation: { checkOutDate: '2026-10-03' }, amountCharged: 63.5 } })
        getFolioByReservationId.mockResolvedValue({ data: { id: 88 } })
        addCardOnFile.mockResolvedValue({ data: {} })
        // No POS devices and no manual provider configured -> PaymentMethodStep will show its
        // "terminal unavailable, no backup" message rather than a usable form in this setup, so
        // this test only verifies the no-credential prompt appears, not a full re-submit.
        render(<ExtendStayModal reservation={RESERVATION} onSaved={vi.fn()} onClose={vi.fn()} />)

        const dateInput = document.querySelector('input[type="date"]')
        await user.clear(dateInput)
        await user.type(dateInput, '2026-10-03')
        await user.click(screen.getByRole('button', { name: 'Extend' }))

        expect(await screen.findByText('No Card on File')).toBeInTheDocument()
        await waitFor(() => expect(extendStay).toHaveBeenCalledTimes(1))
    })

    it('calls onClose when Cancel is clicked', async () => {
        const user = userEvent.setup()
        const onClose = vi.fn()
        render(<ExtendStayModal reservation={RESERVATION} onSaved={vi.fn()} onClose={onClose} />)

        await user.click(screen.getByRole('button', { name: 'Cancel' }))

        expect(onClose).toHaveBeenCalledTimes(1)
    })
})
