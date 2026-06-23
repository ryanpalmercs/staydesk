import { useState, useEffect } from 'react'
import { format, parse, startOfWeek, getDay } from 'date-fns'
import { enUS } from 'date-fns/locale'
import { getRooms } from '../api/roomApi'
import { getReservations, checkIn } from '../api/reservationApi'
import { getGuests } from '../api/guestApi'
import './DashboardPage.css'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import { Link, useNavigate } from 'react-router-dom'
import interactionPlugin from '@fullcalendar/interaction'
import ReservationSummaryModal from '../components/ReservationSummaryModal'
import CheckInPaymentModal from '../components/CheckInPaymentModal'

const STATUS_COLORS = {
    CONFIRMED: { backgroundColor: '#F0E0C8', textColor: '#7A4E2D', borderColor: '#F0E0C8' },
    CHECKED_IN: { backgroundColor: '#dcfce7', textColor: '#166534', borderColor: '#dcfce7' },
    CHECKED_OUT: { backgroundColor: '#f3f4f6', textColor: '#4b5563', borderColor: '#f3f4f6' },
}

function DashboardPage() {
    const [rooms, setRooms] = useState([])
    const [reservations, setReservations] = useState([])
    const [guests, setGuests] = useState([])
    const [calDate, setCalDate] = useState(new Date())
    const [calView, setCalView] = useState('month')
    const navigate = useNavigate()
    const [selectedEvent, setSelectedEvent] = useState(null)
    const [checkInTarget, setCheckInTarget] = useState(null)

    function fetchData() {
        Promise.all([getRooms(), getReservations(), getGuests()])
            .then(([r, res, g]) => {
                setRooms(r.data)
                setReservations(res.data)
                setGuests(g.data)
            })
    }

    useEffect(() => { fetchData() }, [])

    async function handleCheckInConfirmed(roomPaymentMethodId, incidentalsPaymentMethodId) {
        try {
            await checkIn(checkInTarget, roomPaymentMethodId, incidentalsPaymentMethodId)
            setCheckInTarget(null)
            setSelectedEvent(null)
            fetchData()
        } catch (err) {
            console.error('Check-in failed:', err, 'checkInTarget:', checkInTarget)
            throw err
        }
    }

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
            allDay: true,
            ...STATUS_COLORS[r.status],
            extendedProps: {
                reservationId: r.id,
                status: r.status,
                guestId: r.guestId,
                roomId: r.roomId,
                checkInDate: r.checkInDate,
                checkOutDate: r.checkOutDate
            }
        }))

    return (
        <div className="dashboard">
            <h1 className="section-title">Dashboard</h1>

            <div className="dashboard-stats">
                <Link to="/rooms" className="stat-card">
                    <div>
                        <div className="stat-label">Occupancy</div>
                        <div className="stat-value">{occupiedCount} / {rooms.length}</div>
                        <div className="stat-sub">{availableCount} available</div>
                    </div>
                </Link>
                <Link to="/reservations" className="stat-card">
                    <div>
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
                </Link>
                <Link to="/reservations" className="stat-card">
                    <div>
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
                </Link>
            </div>

            <div className="dashboard-calendar">
                <FullCalendar
                    plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
                    initialView="dayGridMonth"
                    headerToolbar={{
                        left: 'prev,next today',
                        center: 'title',
                        right: 'dayGridMonth,timeGridWeek'
                    }}
                    events={events}
                    eventContent={arg => (
                        <div
                            onClick={() => setSelectedEvent(arg.event.extendedProps)}
                            style={{ cursor: 'pointer', padding: '2px 4px', width: '100%', fontSize: '0.85em' }}
                        >
                            {arg.event.title}
                        </div>
                    )}
                    height="auto"
                />
            </div>

            {selectedEvent && !checkInTarget && (
                <ReservationSummaryModal
                    reservation={selectedEvent}
                    guest={guestsMap[selectedEvent.guestId]}
                    room={roomsMap[selectedEvent.roomId]}
                    onClose={() => setSelectedEvent(null)}
                    onCheckIn={() => setCheckInTarget(selectedEvent.reservationId)}
                    onNavigate={navigate}
                />
            )}

            {checkInTarget && (
                <CheckInPaymentModal
                    onConfirm={handleCheckInConfirmed}
                    onClose={() => setCheckInTarget(null)}
                />
            )}
        </div>
    )
}

export default DashboardPage