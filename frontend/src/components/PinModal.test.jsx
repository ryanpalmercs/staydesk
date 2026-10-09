import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PinModal from './PinModal'

vi.mock('../api/employeeApi', () => ({
    updateEmployeePin: vi.fn()
}))

import { updateEmployeePin } from '../api/employeeApi'

const EMPLOYEE = { id: 'e1', doorAccessEnabled: false }

describe('PinModal', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        updateEmployeePin.mockResolvedValue({})
    })

    it('shows a mismatch error and does not call the API when the PINs differ', async () => {
        const user = userEvent.setup()
        render(<PinModal employee={EMPLOYEE} onSaved={vi.fn()} onClose={vi.fn()} />)

        await user.type(screen.getByLabelText('PIN'), '482913')
        await user.type(screen.getByLabelText('Confirm PIN'), '111111')
        await user.click(screen.getByRole('button', { name: 'Submit' }))

        expect(await screen.findByText('PINs do not match')).toBeInTheDocument()
        expect(updateEmployeePin).not.toHaveBeenCalled()
    })

    it('submits the new PIN and door-access flag when both PINs match', async () => {
        const user = userEvent.setup()
        const onSaved = vi.fn()
        render(<PinModal employee={EMPLOYEE} onSaved={onSaved} onClose={vi.fn()} />)

        await user.type(screen.getByLabelText('PIN'), '482913')
        await user.type(screen.getByLabelText('Confirm PIN'), '482913')
        await user.click(screen.getByLabelText('Grant smart lock door access with this PIN'))
        await user.click(screen.getByRole('button', { name: 'Submit' }))

        expect(updateEmployeePin).toHaveBeenCalledWith('e1', { pin: '482913', grantDoorAccess: true })
        expect(onSaved).toHaveBeenCalledTimes(1)
    })

    it('disables submit until the form is actually changed', async () => {
        const user = userEvent.setup()
        render(<PinModal employee={EMPLOYEE} onSaved={vi.fn()} onClose={vi.fn()} />)

        expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled()

        await user.type(screen.getByLabelText('PIN'), '4')
        expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled()
    })
})
