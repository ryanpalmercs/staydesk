import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RoomModal from './RoomModal'

vi.mock('../api/roomApi', () => ({
    createRoom: vi.fn(),
    updateRoom: vi.fn()
}))
vi.mock('../api/roomTypeApi', () => ({
    getRoomTypes: vi.fn()
}))
vi.mock('../api/sifelyApi', () => ({
    getSifelyLocks: vi.fn()
}))
// RoomModal calls useAuth() (from contexts/AuthContext) to gate lock-management fields on role.
// AuthContext imports lib/supabase.js, which calls createClient() at import time and throws in
// Vitest since VITE_SUPABASE_URL isn't set - so the whole context is mocked here rather than
// letting it load for real.
vi.mock('../contexts/AuthContext', () => ({
    useAuth: vi.fn()
}))

import { createRoom, updateRoom } from '../api/roomApi'
import { getRoomTypes } from '../api/roomTypeApi'
import { getSifelyLocks } from '../api/sifelyApi'
import { useAuth } from '../contexts/AuthContext'

const ROOM_TYPES = [
    { id: 1, name: 'SINGLE_QUEEN' },
    { id: 2, name: 'DOUBLE_FULL' }
]

describe('RoomModal - non-admin (no lock management)', () => {
    beforeEach(() => {
        useAuth.mockReturnValue({ role: 'FRONT_DESK' })
        getRoomTypes.mockResolvedValue({ data: ROOM_TYPES })
        getSifelyLocks.mockResolvedValue({ data: [] })
        createRoom.mockResolvedValue({ data: { id: 55 } })
        updateRoom.mockResolvedValue({ data: {} })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    it('does not show the Door Lock field and does not fetch Sifely locks for a non-admin', async () => {
        render(<RoomModal room={null} onSaved={vi.fn()} onClose={vi.fn()} />)

        await screen.findByText('SINGLE QUEEN')
        expect(screen.queryByText('Door Lock')).not.toBeInTheDocument()
        expect(getSifelyLocks).not.toHaveBeenCalled()
    })

    it('creates a room with roomNumber and roomTypeId coerced to numbers', async () => {
        const user = userEvent.setup()
        render(<RoomModal room={null} onSaved={vi.fn()} onClose={vi.fn()} />)

        await screen.findByText('SINGLE QUEEN')
        await user.type(document.querySelector('input[name="roomNumber"]'), '101')
        await user.selectOptions(document.querySelector('select[name="roomTypeId"]'), '2')
        await user.click(screen.getByRole('button', { name: 'Add Room' }))

        await waitFor(() => expect(createRoom).toHaveBeenCalledTimes(1))
        const payload = createRoom.mock.calls[0][0]
        expect(payload.roomNumber).toBe(101)
        expect(payload.roomTypeId).toBe(2)
        expect(typeof payload.roomNumber).toBe('number')
    })
})

describe('RoomModal - admin editing an existing room', () => {
    const ROOM = { id: 9, roomNumber: 101, roomTypeId: 1, status: 'AVAILABLE', maintenanceNote: '', sifelyLockId: null }
    const SIFELY_LOCKS = [{ lockId: 501, lockAlias: 'Front Door 101' }]

    beforeEach(() => {
        useAuth.mockReturnValue({ role: 'ADMIN' })
        getRoomTypes.mockResolvedValue({ data: ROOM_TYPES })
        getSifelyLocks.mockResolvedValue({ data: SIFELY_LOCKS })
        updateRoom.mockResolvedValue({ data: {} })
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    it('shows the Door Lock field and fetches Sifely locks for an admin', async () => {
        render(<RoomModal room={ROOM} onSaved={vi.fn()} onClose={vi.fn()} />)

        expect(await screen.findByText('Door Lock')).toBeInTheDocument()
        expect(await screen.findByRole('option', { name: 'Front Door 101' })).toBeInTheDocument()
        expect(getSifelyLocks).toHaveBeenCalledTimes(1)
    })

    it('sends only the fields that actually changed on update, not the whole form', async () => {
        const user = userEvent.setup()
        render(<RoomModal room={ROOM} onSaved={vi.fn()} onClose={vi.fn()} />)

        await screen.findByText('Door Lock')

        const roomNumberInput = document.querySelector('input[name="roomNumber"]')
        await user.clear(roomNumberInput)
        await user.type(roomNumberInput, '102')

        await user.click(screen.getByRole('button', { name: 'Save' }))

        await waitFor(() => expect(updateRoom).toHaveBeenCalledTimes(1))
        const [id, changed] = updateRoom.mock.calls[0]
        expect(id).toBe(9)
        expect(changed).toEqual({ roomNumber: 102 })
    })

    it('disables Save until a field is actually changed', async () => {
        render(<RoomModal room={ROOM} onSaved={vi.fn()} onClose={vi.fn()} />)
        await screen.findByText('Door Lock')

        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    })

    it('disables the maintenance checkbox for an occupied room', async () => {
        render(<RoomModal room={{ ...ROOM, status: 'OCCUPIED' }} onSaved={vi.fn()} onClose={vi.fn()} />)
        await screen.findByText('Door Lock')

        expect(screen.getByRole('checkbox', { name: 'Under maintenance' })).toBeDisabled()
        expect(screen.getByText("This room is currently occupied and can't be marked for maintenance.")).toBeInTheDocument()
    })

    it('requires a maintenance note once the maintenance checkbox is checked', async () => {
        const user = userEvent.setup()
        render(<RoomModal room={ROOM} onSaved={vi.fn()} onClose={vi.fn()} />)
        await screen.findByText('Door Lock')

        await user.click(screen.getByRole('checkbox', { name: 'Under maintenance' }))

        expect(screen.getByText('Maintenance Note')).toBeInTheDocument()
        const noteField = document.querySelector('textarea[name="maintenanceNote"]')
        expect(noteField).toBeRequired()
    })
})
