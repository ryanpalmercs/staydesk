import { useState } from "react"
import { addDays, format } from "date-fns"
import { ArrowLeftIcon, ArrowRightIcon } from "lucide-react"
import Modal from "./Modal"
import StatusBadge from "./StatusBadge"

function todayStr() {
    return new Date().toISOString().split('T')[0]
}

function formatDateLabel(dateStr) {
    const label = new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' })
    return dateStr === todayStr() ? `${label} (Today)` : label
}

function OccupancyModal({ rooms, roomTypesMap, guestsMap, reservations, onClose, onSelectRoom }) {
    const [viewDate, setViewDate] = useState(todayStr)

    function shiftDate(deltaDays) {
        setViewDate(d => format(addDays(new Date(d + 'T12:00:00'), deltaDays), 'yyyy-MM-dd'))
    }

    const isToday = viewDate === todayStr()

    const reservationByRoomId = Object.fromEntries(
        reservations
            .filter(r => r.roomId != null && r.status === 'CHECKED_IN' && (
                isToday || (r.checkInDate <= viewDate && viewDate < r.checkOutDate)
            ))
            .map(r => [r.roomId, r])
    )

    const sortedRooms = [...rooms].sort((a, b) => a.roomNumber - b.roomNumber)

    return (
        <Modal onClose={onClose} size="lg">
            <h2 className="text-lg text-black font-semibold mb-2">Occupancy</h2>

            <div className="flex items-center justify-between mb-4">
                <button
                    type="button"
                    onClick={() => shiftDate(-1)}
                    disabled={viewDate <= todayStr()}
                    className="text-muted hover:text-green disabled:opacity-30 disabled:hover:text-muted"
                    aria-label="Previous day"
                >
                    <ArrowLeftIcon size={20} />
                </button>
                <span className="text-sm font-medium text-black">{formatDateLabel(viewDate)}</span>
                <button type="button" onClick={() => shiftDate(1)} className="text-muted hover:text-green" aria-label="Next day">
                    <ArrowRightIcon size={20} />
                </button>
            </div>

            {!isToday && (
                <p className="text-xs text-muted mb-2">
                    Showing booked occupancy for this date. Maintenance status only reflects rooms' current condition, not future dates.
                </p>
            )}

            <ul className="flex flex-col gap-3 max-h-96 overflow-y-auto">
                {sortedRooms.map(room => {
                    const reservation = reservationByRoomId[room.id]
                    const guest = reservation ? guestsMap[reservation.guestId] : null
                    const roomTypeName = roomTypesMap[room.roomTypeId]?.name.replace('_', ' ') ?? ''
                    const displayStatus = isToday ? room.status : (reservation ? 'OCCUPIED' : 'AVAILABLE')

                    const content = (
                        <>
                            <div className="flex items-center justify-between">
                                <span className="font-semibold text-black">Room {room.roomNumber}</span>
                                <StatusBadge status={displayStatus} />
                            </div>
                            <p className="text-sm text-muted">
                                {guest ? `${guest.firstName} ${guest.lastName}` : roomTypeName}
                            </p>
                        </>
                    )

                    return (
                        <li key={room.id}>
                            {reservation ? (
                                <button
                                    type="button"
                                    onClick={() => onSelectRoom(reservation)}
                                    className="w-full bg-warm-white rounded flex flex-col gap-1 p-4 text-left border-2 border-tan hover:border-black transition-colors"
                                >
                                    {content}
                                </button>
                            ) : (
                                <div className="w-full bg-warm-white rounded flex flex-col gap-1 p-4 text-left border-2 border-tan opacity-70">
                                    {content}
                                </div>
                            )}
                        </li>
                    )
                })}
            </ul>

            <div className="flex justify-end mt-4">
                <button type="button" onClick={onClose} className="btn btn-secondary">Close</button>
            </div>
        </Modal>
    )
}

export default OccupancyModal
