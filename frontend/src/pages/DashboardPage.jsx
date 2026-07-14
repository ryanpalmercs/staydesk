import { useState, useEffect } from 'react'
import { format, parse, startOfWeek, getDay } from 'date-fns'
import { enUS } from 'date-fns/locale'
import { getRooms } from '../api/roomApi'
import { getRoomTypes } from '../api/roomTypeApi'
import { getReservations, checkIn, checkInTerminal, checkOut } from '../api/reservationApi'
import { getGuests } from '../api/guestApi'
import { getFolioByReservationId } from '../api/folioApi'
import './DashboardPage.css'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import { Link, useNavigate } from 'react-router-dom'
import interactionPlugin from '@fullcalendar/interaction'
import ReservationSummaryModal from '../components/ReservationSummaryModal'
import CheckInPaymentModal from '../components/CheckInPaymentModal'
import FolioModal from '../components/FolioModal'

const STATUS_COLORS = {
    CONFIRMED: { backgroundColor: '#F0E0C8', textColor: '#7A4E2D', borderColor: '#F0E0C8' },
    CHECKED_IN: { backgroundColor: '#dcfce7', textColor: '#166534', borderColor: '#dcfce7' },
    CHECKED_OUT: { backgroundColor: '#f3f4f6', textColor: '#4b5563', borderColor: '#f3f4f6' },
}

function DashboardPage() {
    const [rooms, setRooms] = useState([])
    const [roomTypes, setRoomTypes] = useState([])
    const [reservations, setReservations] = useState([])
    const [guests, setGuests] = useState([])
    const [calDate, setCalDate] = useState(new Date())
    const [calView, setCalView] = useState('month')
    const navigate = useNavigate()
    const [selectedEvent, setSelectedEvent] = useState(null)
    const [checkInTarget, setCheckInTarget] = useState(null)
    const [folioId, setFolioId] = useState(null)
    const [visibleStatuses, setVisibleStatuses] = useState(new Set(['CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT']))

    function fetchData() {
        Promise.all([getRooms(), getRoomTypes(), getReservations(), getGuests()])
            .then(([r, rt, res, g]) => {
                setRooms(r.data)
                setRoomTypes(rt.data)
                setReservations(res.data)
                setGuests(g.data)
            })
    }

    useEffect(() => { fetchData() }, [])

    async function handleCheckInConfirmed(roomId, incidentalsPaymentMethodId) {
        const res = await checkIn(checkInTarget, roomId, incidentalsPaymentMethodId)
        setSelectedEvent(null)
        fetchData()
        return res.data.doorAccessStatus
    }

    async function handleTerminalCheckInConfirmed(roomId, posDeviceId) {
        const res = await checkInTerminal(checkInTarget, roomId, posDeviceId)
        setSelectedEvent(null)
        fetchData()
        return res.data.doorAccessStatus
    }

    async function handleViewFolio() {
        try {
            const res = await getFolioByReservationId(selectedEvent.reservationId)
            setFolioId(res.data.id)
            setSelectedEvent(null)
        } catch (err) {
            console.error('Failed to find folio:', err)
        }
    }

    async function handleCheckOut() {
        try {
            await checkOut(selectedEvent.reservationId)
            const res = await getFolioByReservationId(selectedEvent.reservationId)
            setFolioId(res.data.id)
            setSelectedEvent(null)
            fetchData()
        } catch (err) {
            console.error('Check-out failed:', err)
        }
    }

    function toggleStatus(status) {
        setVisibleStatuses(prev => {
            const next = new Set(prev)
            next.has(status) ? next.delete(status) : next.add(status)
            return next
        })
    }

    const today = new Date().toISOString().split('T')[0]
    const guestsMap = Object.fromEntries(guests.map(g => [g.id, g]))
    const roomsMap = Object.fromEntries(rooms.map(r => [r.id, r]))
    const roomTypesMap = Object.fromEntries(roomTypes.map(rt => [rt.id, rt]))
    const roomLabel = r => roomsMap[r.roomId]
        ? `Rm ${roomsMap[r.roomId].roomNumber}`
        : `${roomTypesMap[r.roomTypeId]?.name.replace('_', ' ') ?? 'Room'} (unassigned)`

    const occupiedCount = rooms.filter(r => r.status === 'OCCUPIED').length
    const availableCount = rooms.filter(r => r.status === 'AVAILABLE').length
    const todayCheckIns = reservations.filter(r => r.checkInDate === today && r.status === 'CONFIRMED')
    const todayCheckOuts = reservations.filter(r => r.checkOutDate === today && r.status === 'CHECKED_IN')
    const events = reservations
        .filter(r => r.status !== 'CANCELLED' && visibleStatuses.has(r.status))
        .map(r => ({
            title: `${guestsMap[r.guestId]?.firstName ?? 'Guest'} — ${roomLabel(r)}`,
            start: new Date(r.checkInDate + 'T12:00:00'),
            end: new Date(r.checkOutDate + 'T12:00:00'),
            allDay: true,
            ...STATUS_COLORS[r.status],
            extendedProps: {
                reservationId: r.id,
                status: r.status,
                guestId: r.guestId,
                roomId: r.roomId,
                roomTypeId: r.roomTypeId,
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
                                    {guestsMap[r.guestId]?.firstName} {guestsMap[r.guestId]?.lastName} — {roomLabel(r)}
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
                                    {guestsMap[r.guestId]?.firstName} {guestsMap[r.guestId]?.lastName} — {roomLabel(r)}
                                </li>
                            ))}
                        </ul>
                    </div>
                </Link>
            </div>

            <div className="dashboard-calendar">
                <div className="flex flex-row justify-center gap-3 mb-3">
                    {[
                        { status: 'CONFIRMED', label: 'Confirmed', color: '#F0E0C8' },
                        { status: 'CHECKED_IN', label: 'Checked In', color: '#dcfce7' },
                        { status: 'CHECKED_OUT', label: 'Checked Out', color: '#f3f4f6' },
                    ].map(({ status, label, color }) => (
                        <button
                            key={status}
                            onClick={() => toggleStatus(status)}
                            className={`flex items-center gap-2 text-sm px-3 py-1.5 rounded border border-tan transition-opacity ${visibleStatuses.has(status) ? 'opacity-100' : 'opacity-40'}`}
                        >
                            <span className="inline-block w-3 h-3 rounded-sm" style={{ backgroundColor: color }} />
                            <span className="text-muted">{label}</span>
                        </button>
                    ))}
                </div>
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
                    roomLabel={roomLabel(selectedEvent)}
                    onClose={() => setSelectedEvent(null)}
                    onCheckIn={() => setCheckInTarget(selectedEvent.reservationId)}
                    onCheckOut={handleCheckOut}
                    onViewFolio={handleViewFolio}
                />
            )}

            {checkInTarget != null && (
                <CheckInPaymentModal
                    reservationId={checkInTarget}
                    reservationChannel={reservations.find(r => r.id === checkInTarget)?.channel}
                    onConfirm={handleCheckInConfirmed}
                    onConfirmTerminal={handleTerminalCheckInConfirmed}
                    onClose={() => setCheckInTarget(null)}
                />
            )}

            {folioId && (
                <FolioModal folioId={folioId} onClose={() => setFolioId(null)} onPaid={fetchData} />
            )}
        </div>
    )
}

export default DashboardPage