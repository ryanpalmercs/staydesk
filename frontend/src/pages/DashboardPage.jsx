import { useState, useEffect } from 'react'
import { format, parse, startOfWeek, getDay } from 'date-fns'
import { enUS } from 'date-fns/locale'
import { getRooms } from '../api/roomApi'
import { getReservations } from '../api/reservationApi'
import { getGuests } from '../api/guestApi'
import './DashboardPage.css'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'

export default function DashboardPage() {
    const [rooms, setRooms] = useState([])
    const [reservations, setReservations] = useState([])
    const [guests, setGuests] = useState([])
    const [calDate, setCalDate] = useState(new Date())
    const [calView, setCalView] = useState('month')

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
    const todayCheckIns = reservations.filter(r => r.checkInDate === today && r.status === 'CONFIRMED')
    const todayCheckOuts = reservations.filter(r => r.checkOutDate === today && r.status === 'CHECKED_IN')

    const events = reservations
        .filter(r => r.status !== 'CANCELLED')
        .map(r => ({
            title: `${guestsMap[r.guestId]?.firstName ?? 'Guest'} — Rm ${roomsMap[r.roomId]?.roomNumber}`,
            start: new Date(r.checkInDate + 'T12:00:00'),
            end: new Date(r.checkOutDate + 'T12:00:00'),
            allDay: true
        }))

    return (
        <div>
            <h1 className="section-title">Dashboard</h1>

            <div className="dashboard-stats">
                <div className="stat-card">
                    <div className="stat-label">Occupancy</div>
                    <div className="stat-value">{occupiedCount} / {rooms.length}</div>
                    <div className="stat-sub">{availableCount} available</div>
                </div>
                <div className="stat-card">
                    <div className="stat-label">Checking In Today</div>
                    <div className="stat-value">{todayCheckIns.length}</div>
                    <ul className="stat-list">
                        {todayCheckIns.slice(0, 5).map(r => (
                            <li key={r.id}>
                                {guestsMap[r.guestId]?.firstName} {guestsMap[r.guestId]?.lastName} — Room {roomsMap[r.roomId]?.roomNumber}
                            </li>
                        ))}
                    </ul>
                </div>
                <div className="stat-card">
                    <div className="stat-label">Checking Out Today</div>
                    <div className="stat-value">{todayCheckOuts.length}</div>
                    <ul className="stat-list">
                        {todayCheckOuts.slice(0, 5).map(r => (
                            <li key={r.id}>
                                {guestsMap[r.guestId]?.firstName} {guestsMap[r.guestId]?.lastName} — Rm {roomsMap[r.roomId]?.roomNumber}
                            </li>
                        ))}
                    </ul>
                </div>
            </div>

            <div className="dashboard-calendar">
                <FullCalendar
                    plugins={[dayGridPlugin, timeGridPlugin]}
                    initialView="dayGridMonth"
                    headerToolbar={{
                        left: 'prev,next today',
                        center: 'title',
                        right: 'dayGridMonth,timeGridWeek'
                    }}
                    events={events}
                    eventColor="var(--color-rust)"
                    height={550}
                />
            </div>
        </div>
    )
}