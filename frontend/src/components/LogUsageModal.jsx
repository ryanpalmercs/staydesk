import { useEffect, useState } from "react"
import { getRooms } from "../api/roomApi"
import { logUsage } from "../api/inventoryApi"
import Modal from "./Modal"

function LogUsageModal({ item, onSaved, onClose }) {
    const [rooms, setRooms] = useState([])
    const [quantity, setQuantity] = useState('')
    const [roomId, setRoomId] = useState('')
    const [note, setNote] = useState('')
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        getRooms()
            .then(res => setRooms((res.data ?? []).sort((a, b) => a.roomNumber - b.roomNumber)))
            .catch(() => setRooms([]))
    }, [])

    async function handleSubmit(e) {
        e.preventDefault()
        setError(null)
        setSubmitting(true)

        try {
            await logUsage(item.itemId, Number(quantity), roomId ? Number(roomId) : null, note || null)
            onSaved()
        } catch {
            setError('Failed to log usage.')
        }

        setSubmitting(false)
    }

    return (
        <Modal onClose={onClose} size="sm">
            <h2 className="text-lg text-black font-semibold mb-4">Log Usage — {item.name}</h2>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div>
                    <label className="block text-sm text-muted mb-1">Quantity used ({item.unit})</label>
                    <input type="number" min="1" value={quantity} onChange={e => setQuantity(e.target.value)}
                        required className="filter-input" autoFocus />
                </div>
                <div>
                    <label className="block text-sm text-muted mb-1">Room (optional)</label>
                    <select value={roomId} onChange={e => setRoomId(e.target.value)} className="filter-input">
                        <option value="">Not room-specific</option>
                        {rooms.map(room => (
                            <option key={room.id} value={room.id}>Room {room.roomNumber}</option>
                        ))}
                    </select>
                </div>
                <div>
                    <label className="block text-sm text-muted mb-1">Note (optional)</label>
                    <input value={note} onChange={e => setNote(e.target.value)} className="filter-input" />
                </div>

                {error && <p className="text-sm text-error">{error}</p>}

                <div className="flex justify-end gap-3 mt-2">
                    <button type="button" onClick={onClose} className="btn btn-secondary">Cancel</button>
                    <button type="submit" className="btn btn-primary" disabled={submitting}>
                        {submitting ? 'Saving...' : 'Log Usage'}
                    </button>
                </div>
            </form>
        </Modal>
    )
}

export default LogUsageModal
