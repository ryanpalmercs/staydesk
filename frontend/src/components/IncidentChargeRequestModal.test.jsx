import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import IncidentChargeRequestModal from './IncidentChargeRequestModal'

vi.mock('../api/folioApi', () => ({
    requestIncidentCharge: vi.fn()
}))

import { requestIncidentCharge } from '../api/folioApi'

describe('IncidentChargeRequestModal', () => {
    afterEach(() => {
        vi.clearAllMocks()
    })

    it('disables submit until the form has been filled in (isDirty gate)', () => {
        render(<IncidentChargeRequestModal folioId={1} onRequested={vi.fn()} onClose={vi.fn()} />)

        expect(screen.getByRole('button', { name: 'Submit for Approval' })).toBeDisabled()
    })

    it('submits amount as a number and calls onRequested on success', async () => {
        const user = userEvent.setup()
        const onRequested = vi.fn()
        requestIncidentCharge.mockResolvedValue({ data: {} })
        render(<IncidentChargeRequestModal folioId={7} onRequested={onRequested} onClose={vi.fn()} />)

        await user.type(screen.getByRole('spinbutton'), '45.50')
        await user.type(screen.getByRole('textbox'), 'Smoke damage in room')
        await user.click(screen.getByRole('button', { name: 'Submit for Approval' }))

        await waitFor(() => expect(requestIncidentCharge).toHaveBeenCalledTimes(1))
        expect(requestIncidentCharge).toHaveBeenCalledWith(7, 45.5, 'Smoke damage in room')
        expect(onRequested).toHaveBeenCalledTimes(1)
    })

    it('shows a specific message when there is no card on file or the folio is not closed (409)', async () => {
        const user = userEvent.setup()
        requestIncidentCharge.mockRejectedValue({ response: { status: 409 } })
        render(<IncidentChargeRequestModal folioId={7} onRequested={vi.fn()} onClose={vi.fn()} />)

        await user.type(screen.getByRole('spinbutton'), '20')
        await user.type(screen.getByRole('textbox'), 'Broken lamp')
        await user.click(screen.getByRole('button', { name: 'Submit for Approval' }))

        expect(await screen.findByText("No card on file for this stay, or the folio isn't closed.")).toBeInTheDocument()
    })

    it('shows a generic error message for a non-409 failure', async () => {
        const user = userEvent.setup()
        requestIncidentCharge.mockRejectedValue({ response: { status: 500 } })
        render(<IncidentChargeRequestModal folioId={7} onRequested={vi.fn()} onClose={vi.fn()} />)

        await user.type(screen.getByRole('spinbutton'), '20')
        await user.type(screen.getByRole('textbox'), 'Broken lamp')
        await user.click(screen.getByRole('button', { name: 'Submit for Approval' }))

        expect(await screen.findByText('Failed to submit incident charge request.')).toBeInTheDocument()
    })

    it('calls onClose when Cancel is clicked', async () => {
        const user = userEvent.setup()
        const onClose = vi.fn()
        render(<IncidentChargeRequestModal folioId={7} onRequested={vi.fn()} onClose={onClose} />)

        await user.click(screen.getByRole('button', { name: 'Cancel' }))

        expect(onClose).toHaveBeenCalledTimes(1)
    })
})
