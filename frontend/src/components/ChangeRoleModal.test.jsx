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

    it('disables Submit until a different role is selected, then submits the new role', async () => {
        const user = userEvent.setup()
        const onSaved = vi.fn()
        render(<ChangeRoleModal employee={{ id: 5, employeeTypeId: 1 }} onSaved={onSaved} onClose={vi.fn()} />)

        await screen.findByRole('option', { name: 'Front Desk' })
        expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled()

        await user.selectOptions(screen.getByRole('combobox'), '2')
        expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled()

        await user.click(screen.getByRole('button', { name: 'Submit' }))

        expect(updateEmployeeRole).toHaveBeenCalledWith(5, { employeeTypeId: '2' })
        expect(onSaved).toHaveBeenCalledTimes(1)
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
