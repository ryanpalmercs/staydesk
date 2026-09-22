import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ReservationModal from './ReservationModal'

vi.mock('../api/reservationApi', () => ({
    assignRoom: vi.fn(),
    createReservation: vi.fn(),
    createMultiRoomReservation: vi.fn(),
    getCheckInEstimate: vi.fn(),
    getReservationEstimate: vi.fn(),
    getReservationEstimateWithExtras: vi.fn(),
    payFullStayNow: vi.fn(),
    payFullStayNowTerminal: vi.fn(),
    updateReservation: vi.fn()
}))
vi.mock('../api/roomTypeApi', () => ({
    getRoomTypes: vi.fn(),
    getUnavailableRoomTypeIds: vi.fn()
}))
vi.mock('../api/roomApi', () => ({
    getRoom: vi.fn()
}))
vi.mock('../api/guestApi', () => ({
    createGuest: vi.fn(),
    getGuests: vi.fn(),
    updateGuest: vi.fn()
}))
vi.mock('../api/folioApi', () => ({
    getFolioByReservationId: vi.fn(),
    addFolioItem: vi.fn()
}))
vi.mock('../api/featureFlagsApi', () => ({
    getFeatureFlags: vi.fn()
}))
vi.mock('../api/extrasApi', () => ({
    getExtras: vi.fn()
}))
vi.mock('../api/settingsApi', () => ({
    getPropertySetting: vi.fn()
}))
// ReservationModal renders PaymentMethodStep (for the payment step of the booking flow), which
// imports posDeviceApi - and posDeviceApi, like every api/*.js module, transitively imports
// src/lib/supabase.js via baseApi.js. supabase.js calls createClient() at module load time, which
// throws immediately in Vitest since VITE_SUPABASE_URL isn't set there - so this mock is required
// even though this suite never directly calls a posDeviceApi function itself.
vi.mock('../api/posDeviceApi', () => ({
    getPosDevices: vi.fn(),
    getPosDeviceConfig: vi.fn(),
    checkPosDeviceHealth: vi.fn()
}))

// The real date picker wraps react-date-range's calendar, which needs ResizeObserver (not
// present in jsdom) and is awkward to drive from a test. Stubbed here so this suite can test
// ReservationModal's own submit/validation logic in isolation - the calendar widget itself is a
// separate concern from the booking-flow regression this suite exists to catch.
vi.mock('./ReservationDatePicker', () => ({
    default: ({ onRangeSelected }) => {
        const today = new Date()
        const tomorrow = new Date(today)
        tomorrow.setDate(tomorrow.getDate() + 1)
        // Local-time formatting, matching the real ReservationDatePicker's use of date-fns'
        // format() - toISOString() would convert to UTC and can roll over to the wrong calendar
        // day depending on the machine's timezone, silently breaking the "is this today" check
        // this suite depends on.
        const toLocalIso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

        return (
            <button type="button" onClick={() => onRangeSelected({ checkInDate: toLocalIso(today), checkOutDate: toLocalIso(tomorrow) })}>
                Pick today → tomorrow
            </button>
        )
    }
}))

import { createReservation } from '../api/reservationApi'
import { getReservationEstimateWithExtras } from '../api/reservationApi'
import { getRoomTypes, getUnavailableRoomTypeIds } from '../api/roomTypeApi'
import { getGuests } from '../api/guestApi'
import { getFeatureFlags } from '../api/featureFlagsApi'
import { getExtras } from '../api/extrasApi'
import { getPropertySetting } from '../api/settingsApi'
import { getPosDevices, getPosDeviceConfig } from '../api/posDeviceApi'

const GUEST = { id: 7, name: 'Jane Doe', firstName: 'Jane', lastName: 'Doe', email: 'jane@example.com', phoneNumber: '5551234567', flagged: false }
const ROOM_TYPES = [
    { id: 1, name: 'Single Queen', availableCount: 2 },
    { id: 2, name: 'Double Full', availableCount: 1 }
]

describe('ReservationModal - new walk-in reservation', () => {
    beforeEach(() => {
        getRoomTypes.mockResolvedValue({ data: ROOM_TYPES })
        getGuests.mockResolvedValue({ data: [GUEST] })
        getUnavailableRoomTypeIds.mockResolvedValue({ data: [] })
        getFeatureFlags.mockResolvedValue({ data: { multiRoomBookingEnabled: false } })
        getExtras.mockResolvedValue({ data: [] })
        getPropertySetting.mockResolvedValue({ data: { value: 'authorizenet' } })
        getReservationEstimateWithExtras.mockResolvedValue({ data: { total: 84.17 } })
        createReservation.mockResolvedValue({ data: { id: 42 } })
        getPosDevices.mockResolvedValue({ data: [] })
        getPosDeviceConfig.mockResolvedValue({ data: { recordOnly: false } })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    async function fillOutAndSubmitBookingForm(user) {
        render(<ReservationModal reservation={null} onSaved={vi.fn()} onClose={vi.fn()} />)

        await user.click(await screen.findByRole('button', { name: 'Returning Guest' }))
        await user.click(await screen.findByText('JANE DOE'))
        await user.click(await screen.findByRole('button', { name: 'Continue' }))

        await user.click(await screen.findByRole('button', { name: 'Walk-In' }))
        await user.selectOptions(await screen.findByRole('combobox'), '2')
        await user.click(screen.getByRole('button', { name: /Pick today/ }))
        await user.click(screen.getByRole('button', { name: 'Continue' }))
    }

    it('submits with the selected room type as a real id, not multi-room roomLines', async () => {
        const user = userEvent.setup()
        await fillOutAndSubmitBookingForm(user)

        await waitFor(() => expect(createReservation).toHaveBeenCalledTimes(1))

        const payload = createReservation.mock.calls[0][0]
        expect(payload.roomTypeId).toBe(2)
        expect(payload.guestId).toBe(7)
        expect(payload.channel).toBe('WALK_IN')
        expect(Number.isNaN(payload.roomTypeId)).toBe(false)
    })

    it('does not block submission on an unrelated room-type-availability check', async () => {
        const user = userEvent.setup()
        await fillOutAndSubmitBookingForm(user)

        await waitFor(() => expect(createReservation).toHaveBeenCalledTimes(1))
        expect(screen.queryByText('Please select a room type for each room.')).not.toBeInTheDocument()
        expect(screen.queryByText('Please select a room type.')).not.toBeInTheDocument()
    })
})
