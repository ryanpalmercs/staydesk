import { useState } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import useReservationData from '../hooks/useReservationData'
import useReservationActions from '../hooks/useReservationActions'
import ReservationActionModals from '../components/ReservationActionModals'

const STATUS_COLORS = {
    CONFIRMED: { backgroundColor: '#F0E0C8', textColor: '#7A4E2D', borderColor: '#F0E0C8' },
    CHECKED_IN: { backgroundColor: '#dcfce7', textColor: '#166534', borderColor: '#dcfce7' },
    CHECKED_OUT: { backgroundColor: '#f3f4f6', textColor: '#4b5563', borderColor: '#f3f4f6' },
    NO_SHOW: { backgroundColor: '#fee2e2', textColor: '#991b1b', borderColor: '#fee2e2' },
}

function CalendarPage() {
    const [visibleStatuses, setVisibleStatuses] = useState(new Set(['CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT']))

    const data = useReservationData()
    const actions = useReservationActions({ fetchData: data.fetchData })

    function toggleStatus(status) {
        setVisibleStatuses(prev => {
            const next = new Set(prev)
            next.has(status) ? next.delete(status) : next.add(status)
            return next
        })
    }

    const events = data.reservations
        .filter(r => r.status !== 'CANCELLED' && visibleStatuses.has(r.status))
        .map(r => ({
            title: `${data.guestsMap[r.guestId]?.firstName?.toUpperCase() ?? 'Guest'} — ${data.roomLabel(r)}`,
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
                roomNumber: data.roomsMap[r.roomId]?.roomNumber ?? Infinity,
                checkInDate: r.checkInDate,
                checkOutDate: r.checkOutDate
            }
        }))

    return (
        <div className="dashboard">
            <h1 className="section-title">Calendar</h1>
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
                            onClick={() => actions.setSelectedEvent(arg.event.extendedProps)}
                            style={{ cursor: 'pointer', padding: '2px 4px', width: '100%', fontSize: '0.85em' }}
                        >
                            {arg.event.title}
                        </div>
                    )}
                    height="auto"
                />
            </div>

            <ReservationActionModals {...actions} {...data} />
        </div>
    )
}

export default CalendarPage