import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CheckInPaymentModal from './CheckInPaymentModal'

vi.mock('../api/settingsApi', () => ({
    getPropertySetting: vi.fn()
}))
vi.mock('../api/reservationApi', () => ({
    getAvailableRoomsForCheckIn: vi.fn(),
    getCheckInEstimate: vi.fn()
}))
// DoorCode (rendered on the 'code' step) imports lockPasscodeApi, and PaymentMethodStep (rendered
// on the 'payment' step) imports posDeviceApi - both transitively import lib/supabase.js, which
// throws at import time in Vitest since VITE_SUPABASE_URL isn't set there.
vi.mock('../api/lockPasscodeApi', () => ({
    getActiveLockPasscode: vi.fn()
}))
vi.mock('../api/posDeviceApi', () => ({
    getPosDevices: vi.fn(),
    getPosDeviceConfig: vi.fn(),
    checkPosDeviceHealth: vi.fn()
}))

import { getPropertySetting } from '../api/settingsApi'
import { getAvailableRoomsForCheckIn, getCheckInEstimate } from '../api/reservationApi'
import { getPosDevices, getPosDeviceConfig } from '../api/posDeviceApi'
import { getActiveLockPasscode } from '../api/lockPasscodeApi'

const ROOMS = [{ id: 1, roomNumber: '101' }, { id: 2, roomNumber: '102' }]

function pastDateReservation() {
    return { id: 30, checkInDate: '2020-01-01' }
}

function futureDateReservation() {
    return { id: 30, checkInDate: '2099-01-01' }
}

describe('CheckInPaymentModal - check-in due today or already due', () => {
    beforeEach(() => {
        // getPropertySetting is shared by CheckInPaymentModal itself (incidentals_hold_amount)
        // and, once the payment step renders, by PaymentMethodStep (payment_provider) - so it
        // must branch on the setting name rather than returning one fixed value.
        getPropertySetting.mockImplementation(name => Promise.resolve({
            data: { value: name === 'payment_provider' ? 'authorizenet' : '50.00' }
        }))
        getAvailableRoomsForCheckIn.mockResolvedValue({ data: ROOMS })
        getCheckInEstimate.mockResolvedValue({ data: { total: 120, roomChargeDue: true } })
        getPosDevices.mockResolvedValue({ data: [] })
        getPosDeviceConfig.mockResolvedValue({ data: { recordOnly: false } })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    it('goes straight to room assignment (skips the future-check-in warning)', async () => {
        render(
            <CheckInPaymentModal
                reservationId={30}
                reservation={pastDateReservation()}
                onConfirm={vi.fn()}
                onConfirmTerminal={vi.fn()}
                onClose={vi.fn()}
            />
        )

        expect(await screen.findByText('Assign a Room')).toBeInTheDocument()
        expect(screen.getByText('Room 101')).toBeInTheDocument()
        expect(screen.getByText('Room 102')).toBeInTheDocument()
    })

    it('shows a message and no room list when no rooms of the reserved type are available', async () => {
        getAvailableRoomsForCheckIn.mockResolvedValue({ data: [] })
        render(
            <CheckInPaymentModal
                reservationId={30}
                reservation={pastDateReservation()}
                onConfirm={vi.fn()}
                onConfirmTerminal={vi.fn()}
                onClose={vi.fn()}
            />
        )

        expect(await screen.findByText('No rooms of this type are currently available.')).toBeInTheDocument()
    })

    it('moves to the payment step showing the combined total once a room is picked', async () => {
        const user = userEvent.setup()
        render(
            <CheckInPaymentModal
                reservationId={30}
                reservation={pastDateReservation()}
                onConfirm={vi.fn()}
                onConfirmTerminal={vi.fn()}
                onClose={vi.fn()}
            />
        )

        await user.click(await screen.findByText('Room 101'))
        await user.click(screen.getByRole('button', { name: 'Continue' }))

        expect(await screen.findByRole('heading', { name: 'Card for Incidentals' })).toBeInTheDocument()
        // roomChargeDue: true -> chargeAmount = stayTotal (120) + incidentalsHoldAmount (50) = 170,
        // labeled "Total Charge" rather than "Incidentals Hold". AmountBanner only renders once
        // PaymentMethodStep's own async provider/POS-device lookups resolve, hence findByText.
        expect(await screen.findByText('Total Charge')).toBeInTheDocument()
        expect(screen.getByText('$170.00')).toBeInTheDocument()
    })

    // These drive the payment step via PaymentMethodStep's no-terminal/record-only path (no POS
    // devices paired, record-only enabled), which calls onSubmitTerminal(null) - wired in
    // CheckInPaymentModal to onConfirmTerminal(selectedRoomId, deviceId) - so the doorAccessStatus
    // branching (code / door-failed / close) can be exercised end to end without a real card form.
    async function proceedToRecordOnlyPayment(user) {
        await user.click(await screen.findByText('Room 101'))
        await user.click(screen.getByRole('button', { name: 'Continue' }))
        await screen.findByRole('heading', { name: 'Card for Incidentals' })
        await user.click(await screen.findByRole('button', { name: 'Record Charge (No Terminal)' }))
    }

    it('calls onConfirmTerminal with the selected room and shows the door code when access is ISSUED', async () => {
        const user = userEvent.setup()
        getPosDeviceConfig.mockResolvedValue({ data: { recordOnly: true } })
        const onConfirmTerminal = vi.fn().mockResolvedValue('ISSUED')
        getActiveLockPasscode.mockResolvedValue({ data: { passcode: '1234', endDate: '2026-09-25T12:00:00Z' } })
        render(
            <CheckInPaymentModal
                reservationId={30}
                reservation={pastDateReservation()}
                onConfirm={vi.fn()}
                onConfirmTerminal={onConfirmTerminal}
                onClose={vi.fn()}
            />
        )

        await proceedToRecordOnlyPayment(user)

        await waitFor(() => expect(onConfirmTerminal).toHaveBeenCalledWith(1, null))
        expect(await screen.findByRole('heading', { name: 'Door Code' })).toBeInTheDocument()
        expect(await screen.findByText('1234')).toBeInTheDocument()
    })

    it('shows the door-access-failed notice, without blocking check-in, when access issuance FAILED', async () => {
        const user = userEvent.setup()
        getPosDeviceConfig.mockResolvedValue({ data: { recordOnly: true } })
        const onConfirmTerminal = vi.fn().mockResolvedValue('FAILED')
        render(
            <CheckInPaymentModal
                reservationId={30}
                reservation={pastDateReservation()}
                onConfirm={vi.fn()}
                onConfirmTerminal={onConfirmTerminal}
                onClose={vi.fn()}
            />
        )

        await proceedToRecordOnlyPayment(user)

        expect(await screen.findByText("Door lock code couldn't be issued")).toBeInTheDocument()
    })

    it('closes the modal directly when there is no door-access status to report', async () => {
        const user = userEvent.setup()
        getPosDeviceConfig.mockResolvedValue({ data: { recordOnly: true } })
        const onClose = vi.fn()
        const onConfirmTerminal = vi.fn().mockResolvedValue(undefined)
        render(
            <CheckInPaymentModal
                reservationId={30}
                reservation={pastDateReservation()}
                onConfirm={vi.fn()}
                onConfirmTerminal={onConfirmTerminal}
                onClose={onClose}
            />
        )

        await proceedToRecordOnlyPayment(user)

        await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
    })
})

describe('CheckInPaymentModal - future check-in', () => {
    beforeEach(() => {
        getPropertySetting.mockResolvedValue({ data: { value: '50.00' } })
        getAvailableRoomsForCheckIn.mockResolvedValue({ data: ROOMS })
        getCheckInEstimate.mockResolvedValue({ data: { total: 120, roomChargeDue: true } })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    it('warns before checking in a reservation that is not due yet, and lists the charge warning when a room charge is due', async () => {
        render(
            <CheckInPaymentModal
                reservationId={30}
                reservation={futureDateReservation()}
                onConfirm={vi.fn()}
                onConfirmTerminal={vi.fn()}
                onClose={vi.fn()}
            />
        )

        expect(await screen.findByRole('heading', { name: 'Confirm Early Check-In' })).toBeInTheDocument()
        expect(screen.getByText('Charge the full stay to their card')).toBeInTheDocument()
    })

    it('proceeds to room assignment after confirming the early check-in warning', async () => {
        const user = userEvent.setup()
        render(
            <CheckInPaymentModal
                reservationId={30}
                reservation={futureDateReservation()}
                onConfirm={vi.fn()}
                onConfirmTerminal={vi.fn()}
                onClose={vi.fn()}
            />
        )

        await screen.findByRole('heading', { name: 'Confirm Early Check-In' })
        await user.click(screen.getByRole('button', { name: 'Check In Anyway' }))

        expect(await screen.findByText('Assign a Room')).toBeInTheDocument()
    })

    it('calls onClose (not onConfirm) when the early check-in warning is cancelled', async () => {
        const user = userEvent.setup()
        const onClose = vi.fn()
        render(
            <CheckInPaymentModal
                reservationId={30}
                reservation={futureDateReservation()}
                onConfirm={vi.fn()}
                onConfirmTerminal={vi.fn()}
                onClose={onClose}
            />
        )

        await screen.findByRole('heading', { name: 'Confirm Early Check-In' })
        await user.click(screen.getByRole('button', { name: 'Cancel' }))

        expect(onClose).toHaveBeenCalledTimes(1)
    })
})
