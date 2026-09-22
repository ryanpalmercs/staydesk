import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import FolioModal from './FolioModal'

vi.mock('../api/folioApi', () => ({
    getFolio: vi.fn(),
    getFolioItems: vi.fn(),
    addFolioItem: vi.fn(),
    payFolio: vi.fn(),
    getFolioIncidentCharges: vi.fn(),
    getFolioPayments: vi.fn(),
    chargeExtra: vi.fn(),
    chargeExtraTerminal: vi.fn(),
    requestIncidentCharge: vi.fn()
}))
vi.mock('../api/extrasApi', () => ({
    getExtras: vi.fn()
}))
// TerminalOrRecordOnlyStep (rendered when a card-on-file charge fails with 409) imports
// posDeviceApi, which transitively imports lib/supabase.js and throws at import time in Vitest.
vi.mock('../api/posDeviceApi', () => ({
    getPosDevices: vi.fn(),
    getPosDeviceConfig: vi.fn(),
    checkPosDeviceHealth: vi.fn()
}))

import {
    getFolio, getFolioItems, addFolioItem, payFolio, getFolioIncidentCharges, getFolioPayments,
    chargeExtra, chargeExtraTerminal
} from '../api/folioApi'
import { getExtras } from '../api/extrasApi'
import { getPosDevices, getPosDeviceConfig } from '../api/posDeviceApi'

const OPEN_FOLIO = { id: 1, status: 'OPEN', total: 100, paidAt: null }
const ITEMS = [{ id: 1, description: 'Room charge', amount: 100 }]
const EXTRAS = [{ id: 5, name: 'Late Checkout', price: 15, billingType: 'ONE_TIME' }]

describe('FolioModal - open folio, adding extras', () => {
    beforeEach(() => {
        getFolioItems.mockResolvedValue({ data: ITEMS })
        getFolioPayments.mockResolvedValue({ data: [] })
        getExtras.mockResolvedValue({ data: EXTRAS })
        getPosDevices.mockResolvedValue({ data: [] })
        getPosDeviceConfig.mockResolvedValue({ data: { recordOnly: false } })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    it('renders folio items and the total', async () => {
        getFolio.mockResolvedValue({ data: OPEN_FOLIO })
        render(<FolioModal folioId={1} reservationId={9} onClose={vi.fn()} onPaid={vi.fn()} />)

        expect(await screen.findByText('Room charge')).toBeInTheDocument()
        expect(screen.getByText('Total').closest('tr')).toHaveTextContent('$100.00')
    })

    it('adds an extra, then auto-charges the card on file for the resulting increase', async () => {
        const user = userEvent.setup()
        getFolio
            .mockResolvedValueOnce({ data: OPEN_FOLIO })
            .mockResolvedValueOnce({ data: { ...OPEN_FOLIO, total: 115 } })
        addFolioItem.mockResolvedValue({ data: {} })
        chargeExtra.mockResolvedValue({ data: {} })
        render(<FolioModal folioId={1} reservationId={9} onClose={vi.fn()} onPaid={vi.fn()} />)

        await screen.findByText('Room charge')
        await user.selectOptions(screen.getByRole('combobox'), '5')
        await user.click(screen.getByRole('button', { name: 'Add' }))

        await waitFor(() => expect(addFolioItem).toHaveBeenCalledWith(1, 5, 1))
        await waitFor(() => expect(chargeExtra).toHaveBeenCalledWith(1, 9, 15, 'Late Checkout'))
    })

    it('prompts for a terminal/record-only charge when the card-on-file charge fails with 409', async () => {
        const user = userEvent.setup()
        getFolio
            .mockResolvedValueOnce({ data: OPEN_FOLIO })
            .mockResolvedValueOnce({ data: { ...OPEN_FOLIO, total: 115 } })
        addFolioItem.mockResolvedValue({ data: {} })
        chargeExtra.mockRejectedValue({ response: { status: 409 } })
        getPosDeviceConfig.mockResolvedValue({ data: { recordOnly: true } })
        render(<FolioModal folioId={1} reservationId={9} onClose={vi.fn()} onPaid={vi.fn()} />)

        await screen.findByText('Room charge')
        await user.selectOptions(screen.getByRole('combobox'), '5')
        await user.click(screen.getByRole('button', { name: 'Add' }))

        expect(await screen.findByText('No card on file for this amount — collect it now so the folio stays accurate.')).toBeInTheDocument()
        expect(screen.getByText('Record Charge (No Terminal)')).toBeInTheDocument()
    })

    it('shows a generic error when addFolioItem fails for a reason other than a closed folio', async () => {
        const user = userEvent.setup()
        getFolio.mockResolvedValue({ data: OPEN_FOLIO })
        addFolioItem.mockRejectedValue({ response: { status: 500 } })
        render(<FolioModal folioId={1} reservationId={9} onClose={vi.fn()} onPaid={vi.fn()} />)

        await screen.findByText('Room charge')
        await user.selectOptions(screen.getByRole('combobox'), '5')
        await user.click(screen.getByRole('button', { name: 'Add' }))

        expect(await screen.findByText('Failed to add item.')).toBeInTheDocument()
    })
})

describe('FolioModal - closed folio', () => {
    const CLOSED_FOLIO = { id: 2, status: 'CLOSED', total: 200, paidAt: null }

    beforeEach(() => {
        getFolio.mockResolvedValue({ data: CLOSED_FOLIO })
        getFolioItems.mockResolvedValue({ data: ITEMS })
        getFolioPayments.mockResolvedValue({ data: [] })
        getExtras.mockResolvedValue({ data: EXTRAS })
        getFolioIncidentCharges.mockResolvedValue({ data: [{ id: 1, reason: 'Broken lamp', amount: 50, status: 'PENDING' }] })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    it('fetches and shows incident charges only for a closed folio', async () => {
        render(<FolioModal folioId={2} reservationId={9} onClose={vi.fn()} onPaid={vi.fn()} />)

        expect(await screen.findByText('Broken lamp')).toBeInTheDocument()
        expect(getFolioIncidentCharges).toHaveBeenCalledWith(2)
    })

    it('shows Capture Payment when the folio is closed and unpaid, and calls onPaid + onClose on success', async () => {
        const user = userEvent.setup()
        const onPaid = vi.fn()
        const onClose = vi.fn()
        payFolio.mockResolvedValue({ data: {} })
        render(<FolioModal folioId={2} reservationId={9} onClose={onClose} onPaid={onPaid} />)

        const captureButton = await screen.findByRole('button', { name: 'Capture Payment' })
        await user.click(captureButton)

        await waitFor(() => expect(payFolio).toHaveBeenCalledWith(2))
        expect(onPaid).toHaveBeenCalledTimes(1)
        expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('shows a payment-capture error and re-enables the button on failure, without closing', async () => {
        const user = userEvent.setup()
        const onClose = vi.fn()
        payFolio.mockRejectedValue(new Error('declined'))
        render(<FolioModal folioId={2} reservationId={9} onClose={onClose} onPaid={vi.fn()} />)

        const captureButton = await screen.findByRole('button', { name: 'Capture Payment' })
        await user.click(captureButton)

        expect(await screen.findByText('Payment capture failed.')).toBeInTheDocument()
        expect(onClose).not.toHaveBeenCalled()
        expect(screen.getByRole('button', { name: 'Capture Payment' })).not.toBeDisabled()
    })

    it('does not show Capture Payment once the folio has already been paid', async () => {
        getFolio.mockResolvedValue({ data: { ...CLOSED_FOLIO, paidAt: '2026-09-20T12:00:00Z' } })
        render(<FolioModal folioId={2} reservationId={9} onClose={vi.fn()} onPaid={vi.fn()} />)

        await screen.findByText('Broken lamp')
        expect(screen.queryByRole('button', { name: 'Capture Payment' })).not.toBeInTheDocument()
    })

    it('shows failed payments with their failure reason', async () => {
        getFolioPayments.mockResolvedValue({ data: [{ id: 1, kind: 'ROOM', status: 'FAILED', failureReason: 'Card declined' }] })
        render(<FolioModal folioId={2} reservationId={9} onClose={vi.fn()} onPaid={vi.fn()} />)

        expect(await screen.findByText(/ROOM payment failed: Card declined/)).toBeInTheDocument()
    })

    it('opens the Charge Incident modal from the closed-folio actions', async () => {
        const user = userEvent.setup()
        render(<FolioModal folioId={2} reservationId={9} onClose={vi.fn()} onPaid={vi.fn()} />)

        await user.click(await screen.findByRole('button', { name: 'Charge Incident' }))

        expect(screen.getByRole('heading', { name: 'Charge Incident' })).toBeInTheDocument()
        expect(screen.getByText('Submit for Approval')).toBeInTheDocument()
    })
})
