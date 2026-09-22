import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { format } from 'date-fns'
import OccupancyModal from './OccupancyModal'

const ROOMS = [
    { id: 1, roomNumber: 102, roomTypeId: 10, status: 'AVAILABLE' },
    { id: 2, roomNumber: 101, roomTypeId: 10, status: 'OCCUPIED' }
]
const ROOM_TYPES_MAP = { 10: { id: 10, name: 'SINGLE_QUEEN' } }
const GUESTS_MAP = { 77: { firstName: 'Jane', lastName: 'Doe' } }

function todayStr() {
    return format(new Date(), 'yyyy-MM-dd')
}

function futureStr(daysFromNow) {
    const d = new Date()
    d.setDate(d.getDate() + daysFromNow)
    return format(d, 'yyyy-MM-dd')
}

const RESERVATIONS = [
    { id: 5, roomId: 2, guestId: 77, status: 'CHECKED_IN', checkInDate: todayStr(), checkOutDate: futureStr(3) }
]

describe('OccupancyModal', () => {
    it('renders rooms sorted by room number, not input order', () => {
        render(
            <OccupancyModal
                rooms={ROOMS}
                roomTypesMap={ROOM_TYPES_MAP}
                guestsMap={GUESTS_MAP}
                reservations={RESERVATIONS}
                onClose={vi.fn()}
                onSelectRoom={vi.fn()}
            />
        )

        const roomLabels = screen.getAllByText(/^Room \d+$/).map(el => el.textContent)
        expect(roomLabels).toEqual(['Room 101', 'Room 102'])
    })

    it('shows the checked-in guest name for an occupied room, and the room type for a vacant one', () => {
        render(
            <OccupancyModal
                rooms={ROOMS}
                roomTypesMap={ROOM_TYPES_MAP}
                guestsMap={GUESTS_MAP}
                reservations={RESERVATIONS}
                onClose={vi.fn()}
                onSelectRoom={vi.fn()}
            />
        )

        expect(screen.getByText('JANE DOE')).toBeInTheDocument()
        expect(screen.getByText('SINGLE QUEEN')).toBeInTheDocument()
    })

    it('calls onSelectRoom with the reservation when a room with a guest is clicked', async () => {
        const user = userEvent.setup()
        const onSelectRoom = vi.fn()
        render(
            <OccupancyModal
                rooms={ROOMS}
                roomTypesMap={ROOM_TYPES_MAP}
                guestsMap={GUESTS_MAP}
                reservations={RESERVATIONS}
                onClose={vi.fn()}
                onSelectRoom={onSelectRoom}
            />
        )

        await user.click(screen.getByText('JANE DOE'))

        expect(onSelectRoom).toHaveBeenCalledWith(RESERVATIONS[0])
    })

    it('does not render a vacant room as a clickable button', () => {
        render(
            <OccupancyModal
                rooms={ROOMS}
                roomTypesMap={ROOM_TYPES_MAP}
                guestsMap={GUESTS_MAP}
                reservations={RESERVATIONS}
                onClose={vi.fn()}
                onSelectRoom={vi.fn()}
            />
        )

        expect(screen.queryByRole('button', { name: /Room 102/ })).not.toBeInTheDocument()
        expect(screen.getByRole('button', { name: /Room 101/ })).toBeInTheDocument()
    })

    it('re-derives occupancy from booking dates, not room.status, once viewing a future date', async () => {
        const user = userEvent.setup()
        // 5 days out: still within the reservation's stay (checkIn today, checkOut in 3 days is
        // in the past relative to +5 - use a longer stay so this covers the future date window).
        const longStayReservation = { id: 9, roomId: 1, guestId: 77, status: 'CHECKED_IN', checkInDate: todayStr(), checkOutDate: futureStr(10) }
        render(
            <OccupancyModal
                rooms={ROOMS}
                roomTypesMap={ROOM_TYPES_MAP}
                guestsMap={GUESTS_MAP}
                reservations={[longStayReservation]}
                onClose={vi.fn()}
                onSelectRoom={vi.fn()}
            />
        )

        // Room 102 (id 1) has status AVAILABLE but is booked for the next 10 days.
        await user.click(screen.getByRole('button', { name: 'Next day' }))

        expect(await screen.findByText('Showing booked occupancy for this date. Maintenance status only reflects rooms\' current condition, not future dates.')).toBeInTheDocument()
        // Room 102 should now show as occupied by the reservation despite its AVAILABLE status.
        const room102Button = screen.getByRole('button', { name: /Room 102/ })
        expect(room102Button).toHaveTextContent('JANE DOE')
    })

    it('calls onClose when Close is clicked', async () => {
        const user = userEvent.setup()
        const onClose = vi.fn()
        render(
            <OccupancyModal
                rooms={ROOMS}
                roomTypesMap={ROOM_TYPES_MAP}
                guestsMap={GUESTS_MAP}
                reservations={RESERVATIONS}
                onClose={onClose}
                onSelectRoom={vi.fn()}
            />
        )

        await user.click(screen.getByRole('button', { name: 'Close' }))

        expect(onClose).toHaveBeenCalledTimes(1)
    })
})
