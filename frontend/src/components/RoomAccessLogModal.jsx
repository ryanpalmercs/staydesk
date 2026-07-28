import { useEffect, useState } from 'react'
import { getRoomAccessLog } from '../api/roomApi'
import RoomAccessLogTable from './RoomAccessLogTable'
import Modal from './Modal'

function RoomAccessLogModal({ room, onClose }) {
    const [events, setEvents] = useState([])
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        getRoomAccessLog(room.id, 7).then(res => {
            setEvents(res.data)
            setLoading(false)
        })
    }, [room.id])

    return (
        <Modal onClose={onClose} size="xl" scrollable>
            <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg text-black font-semibold">Room {room.roomNumber} — Access Log</h2>
                <button onClick={onClose} className="text-muted hover:text-green text-sm font-medium">Close</button>
            </div>

            <p className="text-sm text-muted mb-4">Last 7 days</p>

            <div className="flex-1 min-h-0 overflow-y-auto overflow-x-auto">
                {loading ? (
                    <p className="text-sm text-muted">Loading...</p>
                ) : (
                    <RoomAccessLogTable events={events} />
                )}
            </div>

            <div className="flex justify-between items-center mt-4 pt-4 border-t border-tan">
                <a href={`/rooms/${room.id}/access-log`} target="_blank" rel="noreferrer"
                   className="text-sm text-muted hover:text-green font-medium">
                    View full history →
                </a>
                <button onClick={onClose} className="btn btn-secondary">Close</button>
            </div>
        </Modal>
    )
}

export default RoomAccessLogModal