import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import EmployeeModal from './EmployeeModal'

vi.mock('../api/employeeApi', () => ({
    createEmployee: vi.fn(),
    getEmployeeTypes: vi.fn(),
    getPayRateTypes: vi.fn(),
    updateEmployeePersonalInfo: vi.fn()
}))

import { createEmployee, getEmployeeTypes, getPayRateTypes, updateEmployeePersonalInfo } from '../api/employeeApi'

const EMPLOYEE_TYPES = [{ id: 1, name: 'Front Desk' }, { id: 2, name: 'Admin' }]
const PAY_RATE_TYPES = [{ value: 'HOURLY', displayName: 'Hourly' }, { value: 'SALARY', displayName: 'Salary' }]

async function fillNewEmployeeBaseFields(user) {
    await user.type(screen.getByPlaceholderText('First Name'), 'Alex')
    await user.type(screen.getByPlaceholderText('Last Name'), 'Rivera')
    await user.type(screen.getByPlaceholderText('Address Line 1'), '123 Main St')
    await user.type(screen.getByPlaceholderText('City'), 'Brookfield')
    await user.type(screen.getByPlaceholderText('State'), 'MO')
    await user.type(screen.getByPlaceholderText('ZIP'), '63015')
    await user.type(document.querySelector('input[name="email"]'), 'alex@example.com')
    await user.type(document.querySelector('input[name="username"]'), 'alexr')
    await user.selectOptions(document.querySelector('select[name="employeeTypeId"]'), '1')
    await user.type(document.querySelector('input[name="hireDate"]'), '2026-01-15')
    await user.type(document.querySelector('input[name="payRate"]'), '15')
}

describe('EmployeeModal - new employee', () => {
    beforeEach(() => {
        getEmployeeTypes.mockResolvedValue({ data: EMPLOYEE_TYPES })
        getPayRateTypes.mockResolvedValue({ data: PAY_RATE_TYPES })
        createEmployee.mockResolvedValue({ data: { id: 99 } })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    it('blocks creation when the PIN and confirmation PIN do not match', async () => {
        const user = userEvent.setup()
        render(<EmployeeModal employee={null} onSaved={vi.fn()} onClose={vi.fn()} />)
        await screen.findByRole('option', { name: 'Front Desk' })

        await fillNewEmployeeBaseFields(user)
        await user.type(screen.getByPlaceholderText('PIN'), '482913')
        await user.type(screen.getByPlaceholderText('Confirm PIN'), '111111')

        await user.click(screen.getByRole('button', { name: 'Add Employee' }))

        expect(await screen.findByText('PINs do not match')).toBeInTheDocument()
        expect(createEmployee).not.toHaveBeenCalled()
    })

    it('submits contactInfo as a nested object when PINs match', async () => {
        const user = userEvent.setup()
        render(<EmployeeModal employee={null} onSaved={vi.fn()} onClose={vi.fn()} />)
        await screen.findByRole('option', { name: 'Front Desk' })

        await fillNewEmployeeBaseFields(user)
        await user.type(screen.getByPlaceholderText('PIN'), '482913')
        await user.type(screen.getByPlaceholderText('Confirm PIN'), '482913')

        await user.click(screen.getByRole('button', { name: 'Add Employee' }))

        await waitFor(() => expect(createEmployee).toHaveBeenCalledTimes(1))
        const payload = createEmployee.mock.calls[0][0]
        expect(payload.contactInfo).toEqual({
            phone: '',
            addressLine1: '123 Main St',
            addressLine2: '',
            city: 'Brookfield',
            state: 'MO',
            zipCode: '63015'
        })
        expect(payload.firstName).toBe('Alex')
        expect(payload.pin).toBe('482913')
    })
})

describe('EmployeeModal - editing an existing employee', () => {
    const EMPLOYEE = {
        id: 3,
        firstName: 'Jordan',
        lastName: 'Lee',
        email: 'jordan@example.com',
        username: 'jlee',
        employeeTypeId: 2,
        payRate: '20.00',
        payRateType: 'HOURLY',
        hireDate: '2025-06-01',
        contactInfo: { phone: '5551234567', addressLine1: '1 Oak Ave', addressLine2: '', city: 'Brookfield', state: 'MO', zipCode: '63015' }
    }

    beforeEach(() => {
        getEmployeeTypes.mockResolvedValue({ data: EMPLOYEE_TYPES })
        getPayRateTypes.mockResolvedValue({ data: PAY_RATE_TYPES })
        updateEmployeePersonalInfo.mockResolvedValue({ data: {} })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    it('disables Update until a field actually changes', async () => {
        render(<EmployeeModal employee={EMPLOYEE} onSaved={vi.fn()} onClose={vi.fn()} />)
        await screen.findByText('jordan@example.com')

        expect(screen.getByRole('button', { name: 'Update Employee' })).toBeDisabled()
    })

    it('enables Update and submits nested contactInfo after editing a field', async () => {
        const user = userEvent.setup()
        const onSaved = vi.fn()
        render(<EmployeeModal employee={EMPLOYEE} onSaved={onSaved} onClose={vi.fn()} />)
        await screen.findByText('jordan@example.com')

        const cityInput = document.querySelector('input[name="city"]')
        await user.clear(cityInput)
        await user.type(cityInput, 'Fenton')

        const updateButton = screen.getByRole('button', { name: 'Update Employee' })
        expect(updateButton).not.toBeDisabled()
        await user.click(updateButton)

        await waitFor(() => expect(updateEmployeePersonalInfo).toHaveBeenCalledTimes(1))
        const [id, payload] = updateEmployeePersonalInfo.mock.calls[0]
        expect(id).toBe(3)
        expect(payload.contactInfo.city).toBe('Fenton')
        expect(onSaved).toHaveBeenCalledTimes(1)
    })

    it('shows an error message if the update fails', async () => {
        const user = userEvent.setup()
        updateEmployeePersonalInfo.mockRejectedValue(new Error('network error'))
        render(<EmployeeModal employee={EMPLOYEE} onSaved={vi.fn()} onClose={vi.fn()} />)
        await screen.findByText('jordan@example.com')

        const cityInput = document.querySelector('input[name="city"]')
        await user.clear(cityInput)
        await user.type(cityInput, 'Fenton')
        await user.click(screen.getByRole('button', { name: 'Update Employee' }))

        expect(await screen.findByText('Failed to update employee')).toBeInTheDocument()
    })
})
