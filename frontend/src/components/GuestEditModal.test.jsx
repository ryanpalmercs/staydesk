import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GuestEditModal from './GuestEditModal'

vi.mock('../api/guestApi', () => ({
    createGuest: vi.fn(),
    updateGuest: vi.fn()
}))

import { createGuest, updateGuest } from '../api/guestApi'

function firstNameInput() { return document.querySelector('input[name="firstName"]') }
function lastNameInput() { return document.querySelector('input[name="lastName"]') }
function phoneInput() { return document.querySelector('input[name="phoneNumber"]') }

describe('GuestEditModal - new guest', () => {
    beforeEach(() => {
        createGuest.mockResolvedValue({ data: { id: 12 } })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    it('requires a legacy pricing amount greater than zero when legacy pricing is checked', async () => {
        const user = userEvent.setup()
        render(<GuestEditModal guest={null} onSaved={vi.fn()} onClose={vi.fn()} />)

        await user.type(firstNameInput(), 'Sam')
        await user.type(lastNameInput(), 'Doe')
        await user.type(phoneInput(), '5551234567')

        await user.click(screen.getByText('Legacy Pricing — grandfather this guest into a fixed rate instead of current pricing.'))
        // "0" satisfies the input's own `required` (non-empty) constraint, so this exercises the
        // component's own `> 0` guard rather than being blocked by jsdom's native form validation.
        await user.type(document.querySelector('input[name="legacyPricingAmount"]'), '0')
        await user.click(screen.getByRole('button', { name: 'Add Guest' }))

        expect(await screen.findByText('Enter a legacy pricing amount greater than zero.')).toBeInTheDocument()
        expect(createGuest).not.toHaveBeenCalled()
    })

    it('clears legacyPricingAmount in the payload when legacy pricing is left unchecked', async () => {
        const user = userEvent.setup()
        render(<GuestEditModal guest={null} onSaved={vi.fn()} onClose={vi.fn()} />)

        await user.type(firstNameInput(), 'Sam')
        await user.type(lastNameInput(), 'Doe')
        await user.type(phoneInput(), '5551234567')

        await user.click(screen.getByRole('button', { name: 'Add Guest' }))

        await waitFor(() => expect(createGuest).toHaveBeenCalledTimes(1))
        expect(createGuest.mock.calls[0][0].legacyPricingAmount).toBeNull()
    })

    it('switches to a single Business Name field and clears lastName when Business Entity is selected', async () => {
        const user = userEvent.setup()
        render(<GuestEditModal guest={null} onSaved={vi.fn()} onClose={vi.fn()} />)

        await user.type(lastNameInput(), 'Doe')
        await user.click(screen.getByRole('button', { name: 'Business Entity' }))

        expect(lastNameInput()).toBeNull()
        expect(screen.getByText('Business Name')).toBeInTheDocument()

        await user.type(firstNameInput(), 'Acme LLC')
        await user.type(phoneInput(), '5551234567')
        await user.click(screen.getByRole('button', { name: 'Add Guest' }))

        await waitFor(() => expect(createGuest).toHaveBeenCalledTimes(1))
        expect(createGuest.mock.calls[0][0].lastName).toBe('')
        expect(createGuest.mock.calls[0][0].guestType).toBe('BUSINESS')
    })

    it('shows a duplicate-email message on a 409', async () => {
        const user = userEvent.setup()
        createGuest.mockRejectedValue({ response: { status: 409 } })
        render(<GuestEditModal guest={null} onSaved={vi.fn()} onClose={vi.fn()} />)

        await user.type(firstNameInput(), 'Sam')
        await user.type(lastNameInput(), 'Doe')
        await user.type(phoneInput(), '5551234567')
        await user.click(screen.getByRole('button', { name: 'Add Guest' }))

        expect(await screen.findByText('A guest with that email already exists.')).toBeInTheDocument()
    })

    it('shows a field-validation message on a 400', async () => {
        const user = userEvent.setup()
        createGuest.mockRejectedValue({ response: { status: 400 } })
        render(<GuestEditModal guest={null} onSaved={vi.fn()} onClose={vi.fn()} />)

        await user.type(firstNameInput(), 'Sam')
        await user.type(lastNameInput(), 'Doe')
        await user.type(phoneInput(), '5551234567')
        await user.click(screen.getByRole('button', { name: 'Add Guest' }))

        expect(await screen.findByText(/phone must be 10 digits/)).toBeInTheDocument()
    })
})

describe('GuestEditModal - editing an existing guest', () => {
    const GUEST = {
        id: 5, firstName: 'Pat', lastName: 'Nguyen', email: 'pat@example.com', phoneNumber: '5559876543',
        smsConsent: true, legacyPricing: false, legacyPricingAmount: '', legacyRateType: 'NIGHTLY',
        regularGuest: false, guestType: 'INDIVIDUAL'
    }

    beforeEach(() => {
        updateGuest.mockResolvedValue({ data: GUEST })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    it('disables Save until a field is actually changed', () => {
        render(<GuestEditModal guest={GUEST} onSaved={vi.fn()} onClose={vi.fn()} />)

        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    })

    it('enables Save and submits an update after editing a field', async () => {
        const user = userEvent.setup()
        const onSaved = vi.fn()
        render(<GuestEditModal guest={GUEST} onSaved={onSaved} onClose={vi.fn()} />)

        const emailInput = document.querySelector('input[name="email"]')
        await user.clear(emailInput)
        await user.type(emailInput, 'newemail@example.com')

        const saveButton = screen.getByRole('button', { name: 'Save' })
        expect(saveButton).not.toBeDisabled()
        await user.click(saveButton)

        await waitFor(() => expect(updateGuest).toHaveBeenCalledTimes(1))
        const [id, payload] = updateGuest.mock.calls[0]
        expect(id).toBe(5)
        expect(payload.email).toBe('newemail@example.com')
        expect(onSaved).toHaveBeenCalledTimes(1)
    })
})
