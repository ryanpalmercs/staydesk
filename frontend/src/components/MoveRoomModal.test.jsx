import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MoveRoomModal from './MoveRoomModal'

vi.mock('../api/reservationApi', () => ({
    assignRoom: vi.fn(),
    moveRoom: vi.fn()
}))
vi.mock('../api/roomApi', () => ({
    getAvailableRooms: vi.fn()
}))
vi.mock('../api/roomTypeApi', () => ({
    getRoomTypes: vi.fn()
}))
vi.mock('./DateNavHeader', () => ({
    todayStr: () => '2026-09-21'
}))

import { assignRoom, moveRoom } from '../api/reservationApi'
import { getAvailableRooms } from '../api/roomApi'
import { getRoomTypes } from '../api/roomTypeApi'

const ROOM_TYPES = [{ id: 1, name: 'Single Queen' }]
const ROOMS = [{ id: 10, roomNumber: 5 }, { id: 11, roomNumber: 6 }, { id: 12, roomNumber: 7 }]

describe('MoveRoomModal', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        getRoomTypes.mockResolvedValue({ data: ROOM_TYPES })
        getAvailableRooms.mockResolvedValue({ data: ROOMS })
        moveRoom.mockResolvedValue({ data: { id: 1 } })
        assignRoom.mockResolvedValue({ data: { id: 1 } })
    })

    it('excludes the reservation\'s current room from the picker', async () => {
        const reservation = { id: 1, roomTypeId: 1, roomId: 11, checkOutDate: '2026-09-26', status: 'CONFIRMED' }
        render(<MoveRoomModal reservation={reservation} onSaved={vi.fn()} onClose={vi.fn()} />)

        await screen.findByRole('button', { name: 'Move Room' })
        expect(screen.getByRole('option', { name: 'Room 5' })).toBeInTheDocument()
        expect(screen.getByRole('option', { name: 'Room 7' })).toBeInTheDocument()
        expect(screen.queryByRole('option', { name: 'Room 6' })).not.toBeInTheDocument()
    })

    it('uses moveRoom (not assignRoom) for a checked-in guest', async () => {
        const user = userEvent.setup()
        const reservation = { id: 1, roomTypeId: 1, roomId: 11, checkOutDate: '2026-09-26', status: 'CHECKED_IN' }
        render(<MoveRoomModal reservation={reservation} onSaved={vi.fn()} onClose={vi.fn()} />)

        const roomSelects = await screen.findAllByRole('combobox')
        await user.selectOptions(roomSelects[1], '10')
        await user.click(screen.getByRole('button', { name: 'Move Room' }))

        await waitFor(() => expect(moveRoom).toHaveBeenCalledWith(1, 10))
        expect(assignRoom).not.toHaveBeenCalled()
    })

    it('uses assignRoom (not moveRoom) for a confirmed-but-not-checked-in reservation', async () => {
        const user = userEvent.setup()
        const reservation = { id: 1, roomTypeId: 1, roomId: 11, checkOutDate: '2026-09-26', status: 'CONFIRMED' }
        render(<MoveRoomModal reservation={reservation} onSaved={vi.fn()} onClose={vi.fn()} />)

        const roomSelects = await screen.findAllByRole('combobox')
        await user.selectOptions(roomSelects[1], '10')
        await user.click(screen.getByRole('button', { name: 'Move Room' }))

        await waitFor(() => expect(assignRoom).toHaveBeenCalledWith(1, 10))
        expect(moveRoom).not.toHaveBeenCalled()
    })

    it('shows a conflict-specific error on a 409', async () => {
        const user = userEvent.setup()
        moveRoom.mockRejectedValue({ response: { status: 409 } })
        const reservation = { id: 1, roomTypeId: 1, roomId: 11, checkOutDate: '2026-09-26', status: 'CHECKED_IN' }
        render(<MoveRoomModal reservation={reservation} onSaved={vi.fn()} onClose={vi.fn()} />)

        const roomSelects = await screen.findAllByRole('combobox')
        await user.selectOptions(roomSelects[1], '10')
        await user.click(screen.getByRole('button', { name: 'Move Room' }))

        expect(await screen.findByText('That room is no longer available.')).toBeInTheDocument()
    })
})
