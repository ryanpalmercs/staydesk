import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AssignRoomModal from './AssignRoomModal'

vi.mock('../api/reservationApi', () => ({
    assignRoom: vi.fn()
}))
vi.mock('../api/roomApi', () => ({
    getAvailableRooms: vi.fn()
}))

import { assignRoom } from '../api/reservationApi'
import { getAvailableRooms } from '../api/roomApi'

const ROOMS = [{ id: 5, roomNumber: 12 }, { id: 6, roomNumber: 14 }]

describe('AssignRoomModal', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        getAvailableRooms.mockResolvedValue({ data: ROOMS })
        assignRoom.mockResolvedValue({ data: { id: 99, roomId: 6 } })
    })

    it('hands the picked room id straight to onSaved without calling the API when there is no reservation yet', async () => {
        const user = userEvent.setup()
        const onSaved = vi.fn()
        render(<AssignRoomModal roomTypeId={1} checkInDate="2026-09-25" checkOutDate="2026-09-26" onSaved={onSaved} onClose={vi.fn()} />)

        await user.selectOptions(await screen.findByRole('combobox'), '6')
        await user.click(screen.getByRole('button', { name: 'Assign Room' }))

        expect(assignRoom).not.toHaveBeenCalled()
        expect(onSaved).toHaveBeenCalledWith(6)
    })

    it('calls the assignRoom API when a reservationId is provided', async () => {
        const user = userEvent.setup()
        const onSaved = vi.fn()
        render(<AssignRoomModal roomTypeId={1} checkInDate="2026-09-25" checkOutDate="2026-09-26" reservationId={99} onSaved={onSaved} onClose={vi.fn()} />)

        await user.selectOptions(await screen.findByRole('combobox'), '6')
        await user.click(screen.getByRole('button', { name: 'Assign Room' }))

        await waitFor(() => expect(assignRoom).toHaveBeenCalledWith(99, 6))
        expect(onSaved).toHaveBeenCalledWith({ id: 99, roomId: 6 })
    })

    it('shows a conflict-specific error on a 409', async () => {
        const user = userEvent.setup()
        assignRoom.mockRejectedValue({ response: { status: 409 } })
        render(<AssignRoomModal roomTypeId={1} checkInDate="2026-09-25" checkOutDate="2026-09-26" reservationId={99} onSaved={vi.fn()} onClose={vi.fn()} />)

        await user.selectOptions(await screen.findByRole('combobox'), '6')
        await user.click(screen.getByRole('button', { name: 'Assign Room' }))

        expect(await screen.findByText('That room is no longer available for these dates.')).toBeInTheDocument()
    })

    it('shows a message when no rooms are available', async () => {
        getAvailableRooms.mockResolvedValue({ data: [] })
        render(<AssignRoomModal roomTypeId={1} checkInDate="2026-09-25" checkOutDate="2026-09-26" onSaved={vi.fn()} onClose={vi.fn()} />)

        expect(await screen.findByText('No rooms of this type are available for these dates.')).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Assign Room' })).toBeDisabled()
    })
})
