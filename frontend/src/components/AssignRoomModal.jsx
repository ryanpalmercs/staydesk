import { useEffect, useState } from "react"
import { assignRoom } from "../api/reservationApi"
import { getAvailableRooms } from "../api/roomApi"
import Modal from "./Modal"

// When reservationId is provided, picking a room calls the assignRoom API immediately (the
// reservation already exists - the "afterward" use, from the reservations list or calendar).
// Without it, picking a room just hands the chosen id back via onSaved - used mid-booking, before
// the reservation exists yet, so the actual assignment happens once it's created.
function AssignRoomModal({ roomTypeId, checkInDate, checkOutDate, reservationId, onSaved, onClose, onBack }) {
    const [rooms, setRooms] = useState([])
    const [roomId, setRoomId] = useState('')
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        getAvailableRooms(roomTypeId, checkInDate, checkOutDate)
            .then(res => setRooms(res.data ?? []))
            .catch(() => setRooms([]))
    }, [roomTypeId, checkInDate, checkOutDate])

    async function handleSubmit(e) {
        e.preventDefault()
        setError(null)

        if (reservationId == null) {
            onSaved(Number(roomId))
            return
        }

        setSubmitting(true)
        try {
            const res = await assignRoom(reservationId, Number(roomId))
            onSaved(res.data)
        } catch (err) {
            setError(err.response?.status === 409 ? 'That room is no longer available for these dates.' : 'Failed to assign room.')
        }

        setSubmitting(false)
    }

    return (
        <Modal onClose={onClose} size="sm">
            <h2 className="text-lg text-black font-semibold mb-4">Assign Room</h2>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div>
                    <label className="block text-sm text-muted mb-1">Room</label>
                    <select value={roomId} onChange={e => setRoomId(e.target.value)} className="filter-input" required>
                        <option value="">Select a room...</option>
                        {rooms.map(room => (
                            <option key={room.id} value={room.id}>Room {room.roomNumber}</option>
                        ))}
                    </select>
                    {rooms.length === 0 && (
                        <p className="text-sm text-muted mt-1">No rooms of this type are available for these dates.</p>
                    )}
                </div>

                {error && <p className="text-sm text-error">{error}</p>}

                <div className="flex justify-between mt-2">
                    {onBack && (
                        <button type="button" onClick={onBack} className="btn btn-secondary">Back</button>
                    )}
                    <div className="flex gap-3 ml-auto">
                        <button type="button" onClick={onClose} className="btn btn-secondary">Skip</button>
                        <button type="submit" className="btn btn-primary" disabled={!roomId || submitting}>
                            {submitting ? 'Assigning...' : 'Assign Room'}
                        </button>
                    </div>
                </div>
            </form>
        </Modal>
    )
}

export default AssignRoomModal
