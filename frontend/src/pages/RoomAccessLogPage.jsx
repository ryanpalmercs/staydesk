import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getRoom, getRoomAccessLog } from '../api/roomApi'
import RoomAccessLogTable from '../components/RoomAccessLogTable'

function RoomAccessLogPage() {
    const { id } = useParams()
    const [room, setRoom] = useState(null)
    const [events, setEvents] = useState([])
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        Promise.all([getRoom(id), getRoomAccessLog(id)]).then(([roomRes, logRes]) => {
            setRoom(roomRes.data)
            setEvents(logRes.data)
            setLoading(false)
        })
    }, [id])

    return (
        <div>
            <div className="page-header mb-6">
                <h1 className="section-title">
                    {room ? `Room ${room.roomNumber} — Access Log` : 'Access Log'}
                </h1>
            </div>

            <p className="text-sm text-muted bg-tan/40 border border-tan rounded px-3 py-2 mb-4">
                Showing the last 90 days. For older records, contact tech support — Sifely's retention window beyond that isn't yet confirmed.
            </p>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : (
                <RoomAccessLogTable events={events} />
            )}
        </div>
    )
}

export default RoomAccessLogPage