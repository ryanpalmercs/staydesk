import { useState, useEffect } from 'react'
import { getRooms } from '../api/roomApi'
import { getReservations } from '../api/reservationApi'
import { getGuests } from '../api/guestApi'

export default function HousekeepingDashboardPage() {
    const [rooms, setRooms] = useState([])
    const [reservations, setReservations] = useState([])
    const [guests, setGuests] = useState([])

    useEffect(() => {
        Promise.all([getRooms(), getReservations(), getGuests()])
            .then(([r, res, g]) => {
                setRooms(r.data)
                setReservations(res.data)
                setGuests(g.data)
            })
    }, [])

    const today = new Date().toISOString().split('T')[0]
    const guestsMap = Object.fromEntries(guests.map(g => [g.id, g]))
    const roomsMap = Object.fromEntries(rooms.map(r => [r.id, r]))

    const occupiedCount = rooms.filter(r => r.status === 'OCCUPIED').length
    const availableCount = rooms.filter(r => r.status === 'AVAILABLE').length
    const todayCheckOuts = reservations.filter(r => r.checkOutDate === today && r.status === 'CHECKED_IN')

    return (
        <div className="dashboard">
            <h1 className="section-title">Housekeeping</h1>

            <div className="dashboard-stats">
                <div className="stat-card">
                    <div className="stat-label">Occupied</div>
                    <div className="stat-value">{occupiedCount}</div>
                </div>
                <div className="stat-card">
                    <div className="stat-label">Available</div>
                    <div className="stat-value">{availableCount}</div>
                </div>
            </div>

            <div style={{ marginTop: '2rem' }}>
                <h2 className="section-title" style={{ fontSize: '1.1rem' }}>Checkouts Today</h2>
                {todayCheckOuts.length === 0 ? (
                    <p className="text-sm text-charcoal/60">No checkouts today.</p>
                ) : (
                    <ul style={{ listStyle: 'none', padding: 0, display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                        {todayCheckOuts.map(r => (
                            <li key={r.id} className="stat-card" style={{ padding: '0.75rem 1rem' }}>
                                Room {roomsMap[r.roomId]?.roomNumber} — {guestsMap[r.guestId]?.firstName} {guestsMap[r.guestId]?.lastName}
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        </div>
    )
}
