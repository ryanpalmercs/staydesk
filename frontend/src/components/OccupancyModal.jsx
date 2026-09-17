import { useState } from "react"
import Modal from "./Modal"
import DateNavHeader, { todayStr } from "./DateNavHeader"
import StatusBadge from "./StatusBadge"
import { formatGuestName } from "../utils/guestName"

function OccupancyModal({ rooms, roomTypesMap, guestsMap, reservations, onClose, onSelectRoom }) {
    const [viewDate, setViewDate] = useState(todayStr)

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

            <DateNavHeader viewDate={viewDate} onChange={setViewDate} minDate={todayStr()} />

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
                                {guest ? formatGuestName(guest) : roomTypeName}
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
