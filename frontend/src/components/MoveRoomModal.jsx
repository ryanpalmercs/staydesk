import { useEffect, useState } from "react"
import { assignRoom, moveRoom } from "../api/reservationApi"
import { getAvailableRooms } from "../api/roomApi"
import { getRoomTypes } from "../api/roomTypeApi"
import { todayStr } from "./DateNavHeader"
import Modal from "./Modal"

// Relocates a guest to a different physical room - same type or a different one (e.g. a flooded
// room forcing a move to whatever's actually available). Room type defaults to the reservation's
// current type but can be changed to see rooms of any other type. Works for a CHECKED_IN guest
// (moveRoom - swaps the door passcode too) or a CONFIRMED reservation with a room already
// pre-assigned (assignRoom - no passcode involved yet).
function MoveRoomModal({ reservation, onSaved, onClose }) {
    const [roomTypes, setRoomTypes] = useState([])
    const [roomTypeId, setRoomTypeId] = useState(reservation.roomTypeId)
    const [rooms, setRooms] = useState([])
    const [roomId, setRoomId] = useState('')
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        getRoomTypes().then(res => setRoomTypes(res.data ?? []))
    }, [])

    useEffect(() => {
        setRoomId('')
        getAvailableRooms(roomTypeId, todayStr(), reservation.checkOutDate)
            .then(res => setRooms((res.data ?? []).filter(room => room.id !== reservation.roomId)))
            .catch(() => setRooms([]))
    }, [roomTypeId, reservation.checkOutDate, reservation.roomId])

    async function handleSubmit(e) {
        e.preventDefault()
        setError(null)
        setSubmitting(true)

        try {
            const action = reservation.status === 'CHECKED_IN' ? moveRoom : assignRoom
            const res = await action(reservation.id, Number(roomId))
            onSaved(res.data)
        } catch (err) {
            setError(err.response?.status === 409 ? 'That room is no longer available.' : 'Failed to move room.')
        }

        setSubmitting(false)
    }

    return (
        <Modal onClose={onClose} size="sm">
            <h2 className="text-lg text-black font-semibold mb-4">Move Room</h2>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div>
                    <label className="block text-sm text-muted mb-1">Room Type</label>
                    <select value={roomTypeId} onChange={e => setRoomTypeId(Number(e.target.value))} className="filter-input">
                        {[...roomTypes].sort((a, b) => a.name.localeCompare(b.name)).map(rt => (
                            <option key={rt.id} value={rt.id}>{rt.name.replace('_', ' ')}</option>
                        ))}
                    </select>
                </div>

                <div>
                    <label className="block text-sm text-muted mb-1">Room</label>
                    <select value={roomId} onChange={e => setRoomId(e.target.value)} className="filter-input" required>
                        <option value="">Select a room...</option>
                        {rooms.map(room => (
                            <option key={room.id} value={room.id}>Room {room.roomNumber}</option>
                        ))}
                    </select>
                    {rooms.length === 0 && (
                        <p className="text-sm text-muted mt-1">No other rooms of this type are currently available.</p>
                    )}
                </div>

                {error && <p className="text-sm text-error">{error}</p>}

                <div className="flex justify-end gap-3 mt-2">
                    <button type="button" onClick={onClose} className="btn btn-secondary">Cancel</button>
                    <button type="submit" className="btn btn-primary" disabled={!roomId || submitting}>
                        {submitting ? 'Moving...' : 'Move Room'}
                    </button>
                </div>
            </form>
        </Modal>
    )
}

export default MoveRoomModal
