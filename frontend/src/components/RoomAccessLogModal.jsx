import { useEffect, useState } from 'react'
import { getRoomAccessLog } from '../api/roomApi'
import RoomAccessLogTable from './RoomAccessLogTable'

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
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
            <div className="bg-warm-white rounded-lg p-6 w-full max-w-2xl max-h-[85vh] shadow-lg border-t-4 border-rust flex flex-col">
                <div className="flex items-center justify-between mb-4">
                    <h2 className="text-lg text-charcoal font-semibold">Room {room.roomNumber} — Access Log</h2>
                    <button onClick={onClose} className="text-muted hover:text-rust text-sm font-medium">Close</button>
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
                       className="text-sm text-brown hover:text-rust font-medium">
                        View full history →
                    </a>
                    <button onClick={onClose} className="btn btn-secondary">Close</button>
                </div>
            </div>
        </div>
    )
}

export default RoomAccessLogModal