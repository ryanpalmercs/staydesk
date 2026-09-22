import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ChangeRoleModal from './ChangeRoleModal'

vi.mock('../api/employeeApi', () => ({
    getEmployeeTypes: vi.fn(),
    updateEmployeeRole: vi.fn()
}))

import { getEmployeeTypes, updateEmployeeRole } from '../api/employeeApi'

const EMPLOYEE_TYPES = [
    { id: 1, name: 'Front Desk' },
    { id: 2, name: 'Admin' }
]

describe('ChangeRoleModal', () => {
    beforeEach(() => {
        getEmployeeTypes.mockResolvedValue({ data: EMPLOYEE_TYPES })
        updateEmployeeRole.mockResolvedValue({ data: {} })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    it('preselects the employee\'s current role once role types load', async () => {
        render(<ChangeRoleModal employee={{ id: 5, employeeTypeId: 2 }} onSaved={vi.fn()} onClose={vi.fn()} />)

        expect(await screen.findByRole('option', { name: 'Admin' })).toBeInTheDocument()
        expect(screen.getByRole('combobox')).toHaveValue('2')
    })

    // Note: the component's Submit button uses disabled={isDirty} - every other modal in this
    // codebase (RoomModal, EmployeeModal, GuestEditModal, IncidentChargeRequestModal) uses
    // disabled={!isDirty} instead. As written, picking a different role makes the form dirty,
    // which immediately re-disables Submit - so a role change can never actually be submitted
    // through this modal's UI. Documenting the current (likely unintended) behavior here rather
    // than silently changing app logic in a test-coverage-only change; flagged separately in the
    // PR description for Ryan to confirm and fix.
    it('disables Submit once a different role is selected, so the change is never actually submitted', async () => {
        const user = userEvent.setup()
        render(<ChangeRoleModal employee={{ id: 5, employeeTypeId: 1 }} onSaved={vi.fn()} onClose={vi.fn()} />)

        await screen.findByRole('option', { name: 'Front Desk' })
        await user.selectOptions(screen.getByRole('combobox'), '2')

        expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled()
        expect(updateEmployeeRole).not.toHaveBeenCalled()
    })

    it('calls onClose when Cancel is clicked', async () => {
        const user = userEvent.setup()
        const onClose = vi.fn()
        render(<ChangeRoleModal employee={{ id: 5, employeeTypeId: 1 }} onSaved={vi.fn()} onClose={onClose} />)

        await screen.findByRole('option', { name: 'Front Desk' })
        await user.click(screen.getByRole('button', { name: 'Cancel' }))

        expect(onClose).toHaveBeenCalledTimes(1)
    })
})
