import { useState, useEffect } from 'react'
import { format, parse, startOfWeek, getDay } from 'date-fns'
import { enUS } from 'date-fns/locale'
import { getRooms } from '../api/roomApi'
import { getRoomTypes } from '../api/roomTypeApi'
import { getReservations, checkIn, checkInTerminal, checkOut } from '../api/reservationApi'
import { getGuests } from '../api/guestApi'
import { formatGuestName } from '../utils/guestName'
import { getFolioByReservationId } from '../api/folioApi'
import './DashboardPage.css'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import { useNavigate } from 'react-router-dom'
import interactionPlugin from '@fullcalendar/interaction'
import ReservationSummaryModal from '../components/ReservationSummaryModal'
import CheckInPaymentModal from '../components/CheckInPaymentModal'
import FolioModal from '../components/FolioModal'
import { useAuth } from '../contexts/AuthContext'
import ExtendStayModal from '../components/ExtendStayModal'
import AssignRoomModal from '../components/AssignRoomModal'
import MoveRoomModal from '../components/MoveRoomModal'
import CheckingInTodayModal from '../components/CheckingInTodayModal'
import CheckingOutTodayModal from '../components/CheckingOutTodayModal'
import OccupancyModal from '../components/OccupancyModal'

const STATUS_COLORS = {
    CONFIRMED: { backgroundColor: '#F0E0C8', textColor: '#7A4E2D', borderColor: '#F0E0C8' },
    CHECKED_IN: { backgroundColor: '#dcfce7', textColor: '#166534', borderColor: '#dcfce7' },
    CHECKED_OUT: { backgroundColor: '#f3f4f6', textColor: '#4b5563', borderColor: '#f3f4f6' },
    NO_SHOW: { backgroundColor: '#fee2e2', textColor: '#991b1b', borderColor: '#fee2e2' },
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
    const [folioReservationId, setFolioReservationId] = useState(null)
    const [extendTarget, setExtendTarget] = useState(null)
    const [assignRoomTarget, setAssignRoomTarget] = useState(null)
    const [moveRoomTarget, setMoveRoomTarget] = useState(null)
    const [visibleStatuses, setVisibleStatuses] = useState(new Set(['CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT']))
    const { displayName } = useAuth()
    const [showCheckingInModal, setShowCheckingInModal] = useState(false)
    const [showCheckingOutModal, setShowCheckingOutModal] = useState(false)
    const [showOccupancyModal, setShowOccupancyModal] = useState(false)

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

    async function handleCheckInConfirmed(roomId, incidentalsPaymentMethodId, roomPaymentMethodId) {
        const res = await checkIn(checkInTarget, roomId, incidentalsPaymentMethodId, roomPaymentMethodId)
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
            setFolioReservationId(selectedEvent.reservationId)
            setSelectedEvent(null)
        } catch (err) {
            console.error('Failed to find folio:', err)
        }
    }

    async function checkOutReservation(reservationId) {
        const res = await checkOut(reservationId)
        const folioRes = await getFolioByReservationId(reservationId)
        setFolioId(folioRes.data.id)
        setFolioReservationId(reservationId)
        fetchData()
        return res
    }

    async function handleCheckOut() {
        try {
            await checkOutReservation(selectedEvent.reservationId)
            setSelectedEvent(null)
        } catch (err) {
            console.error('Check-out failed:', err)
        }
    }

    async function handleCheckOutFromModal(reservationId) {
        try {
            await checkOutReservation(reservationId)
            setShowCheckingOutModal(false)
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

    const today = format(new Date(), 'yyyy-MM-dd')
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
            title: `${guestsMap[r.guestId]?.firstName?.toUpperCase() ?? 'Guest'} — ${roomLabel(r)}`,
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
                roomNumber: roomsMap[r.roomId]?.roomNumber ?? Infinity,
                checkInDate: r.checkInDate,
                checkOutDate: r.checkOutDate
            }
        }))

    return (
        <div className="dashboard">
            {displayName ? (
                <h1 className="section-title">{displayName}'s Dashboard</h1>
            ) : (
                <h1 className="section-title">Dashboard</h1>
            )}

            <div className="dashboard-stats">
                <button type="button" onClick={() => setShowOccupancyModal(true)} className="stat-card text-left">
                    <div>
                        <div className="stat-label">Occupancy</div>
                        <div className="stat-value">{occupiedCount} / {rooms.length}</div>
                        <div className="stat-sub">{availableCount} available</div>
                    </div>
                </button>
                <button type="button" onClick={() => setShowCheckingInModal(true)} className="stat-card text-left">
                    <div>
                        <div className="stat-label">Checking In Today</div>
                        <div className="stat-value">{todayCheckIns.length}</div>
                        <ul className="stat-list">
                            {todayCheckIns.slice(0, 5).map(r => (
                                <li key={r.id}>
                                    {formatGuestName(guestsMap[r.guestId])} — {roomLabel(r)}
                                </li>
                            ))}
                        </ul>
                    </div>
                </button>
                <button type="button" onClick={() => setShowCheckingOutModal(true)} className="stat-card text-left">
                    <div>
                        <div className="stat-label">Checking Out Today</div>
                        <div className="stat-value">{todayCheckOuts.length}</div>
                        <ul className="stat-list">
                            {todayCheckOuts.slice(0, 5).map(r => (
                                <li key={r.id}>
                                    {formatGuestName(guestsMap[r.guestId])} — {roomLabel(r)}
                                </li>
                            ))}
                        </ul>
                    </div>
                </button>
            </div>

            <div className="dashboard-calendar">
                <div className="flex flex-row justify-center gap-3 mb-3">
                    {[
                        { status: 'CONFIRMED', label: 'Confirmed', color: '#F0E0C8' },
                        { status: 'CHECKED_IN', label: 'Checked In', color: '#dcfce7' },
                        { status: 'CHECKED_OUT', label: 'Checked Out', color: '#f3f4f6' },
                        { status: 'NO_SHOW', label: 'No Show', color: '#fee2e2' },
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
                    eventOrder="roomNumber"
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
                    onExtend={() => {
                        setExtendTarget(reservations.find(r => r.id === selectedEvent.reservationId))
                        setSelectedEvent(null)
                    }}
                    onAssignRoom={() => {
                        setAssignRoomTarget(reservations.find(r => r.id === selectedEvent.reservationId))
                        setSelectedEvent(null)
                    }}
                    onMoveRoom={() => {
                        setMoveRoomTarget(reservations.find(r => r.id === selectedEvent.reservationId))
                        setSelectedEvent(null)
                    }}
                />
            )}

            {extendTarget != null && (
                <ExtendStayModal
                    reservation={extendTarget}
                    onSaved={() => { setExtendTarget(null); fetchData() }}
                    onClose={() => setExtendTarget(null)}
                />
            )}

            {assignRoomTarget != null && (
                <AssignRoomModal
                    roomTypeId={assignRoomTarget.roomTypeId}
                    checkInDate={assignRoomTarget.checkInDate}
                    checkOutDate={assignRoomTarget.checkOutDate}
                    reservationId={assignRoomTarget.id}
                    onSaved={() => { setAssignRoomTarget(null); fetchData() }}
                    onClose={() => setAssignRoomTarget(null)}
                />
            )}

            {moveRoomTarget != null && (
                <MoveRoomModal
                    reservation={moveRoomTarget}
                    onSaved={() => { setMoveRoomTarget(null); fetchData() }}
                    onClose={() => setMoveRoomTarget(null)}
                />
            )}

            {checkInTarget != null && (
                <CheckInPaymentModal
                    reservationId={checkInTarget}
                    reservation={reservations.find(r => r.id === checkInTarget)}
                    onConfirm={handleCheckInConfirmed}
                    onConfirmTerminal={handleTerminalCheckInConfirmed}
                    onClose={() => setCheckInTarget(null)}
                />
            )}

            {folioId && (
                <FolioModal folioId={folioId} reservationId={folioReservationId} onClose={() => setFolioId(null)} onPaid={fetchData} />
            )}

            {showCheckingInModal && (
                <CheckingInTodayModal
                    reservations={reservations}
                    guestsMap={guestsMap}
                    roomLabel={roomLabel}
                    onClose={() => setShowCheckingInModal(false)}
                    onCheckIn={reservationId => {
                        setShowCheckingInModal(false)
                        setCheckInTarget(reservationId)
                    }}
                />
            )}

            {showCheckingOutModal && (
                <CheckingOutTodayModal
                    reservations={reservations}
                    guestsMap={guestsMap}
                    roomLabel={roomLabel}
                    onClose={() => setShowCheckingOutModal(false)}
                    onCheckOut={handleCheckOutFromModal}
                    onExtend={reservationId => {
                        setShowCheckingOutModal(false)
                        setExtendTarget(reservations.find(r => r.id === reservationId))
                    }}
                />
            )}

            {showOccupancyModal && (
                <OccupancyModal
                    rooms={rooms}
                    roomTypesMap={roomTypesMap}
                    guestsMap={guestsMap}
                    reservations={reservations}
                    onClose={() => setShowOccupancyModal(false)}
                    onSelectRoom={r => {
                        setShowOccupancyModal(false)
                        setSelectedEvent({
                            reservationId: r.id,
                            status: r.status,
                            guestId: r.guestId,
                            roomId: r.roomId,
                            roomTypeId: r.roomTypeId,
                            roomNumber: roomsMap[r.roomId]?.roomNumber ?? Infinity,
                            checkInDate: r.checkInDate,
                            checkOutDate: r.checkOutDate
                        })
                    }}
                />
            )}

            {showCheckingInModal && (
                <CheckingInTodayModal
                    reservations={reservations}
                    guestsMap={guestsMap}
                    roomLabel={roomLabel}
                    onClose={() => setShowCheckingInModal(false)}
                    onCheckIn={reservationId => {
                        setShowCheckingInModal(false)
                        setCheckInTarget(reservationId)
                    }}
                />
            )}

            {showCheckingOutModal && (
                <CheckingOutTodayModal
                    reservations={reservations}
                    guestsMap={guestsMap}
                    roomLabel={roomLabel}
                    onClose={() => setShowCheckingOutModal(false)}
                    onCheckOut={handleCheckOutFromModal}
                    onExtend={reservationId => {
                        setShowCheckingOutModal(false)
                        setExtendTarget(reservations.find(r => r.id === reservationId))
                    }}
                />
            )}

            {showOccupancyModal && (
                <OccupancyModal
                    rooms={rooms}
                    roomTypesMap={roomTypesMap}
                    guestsMap={guestsMap}
                    reservations={reservations}
                    onClose={() => setShowOccupancyModal(false)}
                    onSelectRoom={r => {
                        setShowOccupancyModal(false)
                        setSelectedEvent({
                            reservationId: r.id,
                            status: r.status,
                            guestId: r.guestId,
                            roomId: r.roomId,
                            roomTypeId: r.roomTypeId,
                            roomNumber: roomsMap[r.roomId]?.roomNumber ?? Infinity,
                            checkInDate: r.checkInDate,
                            checkOutDate: r.checkOutDate
                        })
                    }}
                />
            )}
        </div>
    )
}

export default DashboardPage