import { useRef, useState } from "react"
import Modal from "./Modal"
import DateNavHeader, { todayStr } from "./DateNavHeader"
import { formatGuestName } from "../utils/guestName"
import { useHasOverflow } from "../hooks/useHasOverflow"

function CheckingInTodayModal({ reservations, guestsMap, roomLabel, onClose, onCheckIn }) {
    const [viewDate, setViewDate] = useState(todayStr)
    const [selectedId, setSelectedId] = useState(null)
    const listRef = useRef(null)

    function handleDateChange(date) {
        setViewDate(date)
        setSelectedId(null)
    }

    const dayReservations = reservations.filter(r => r.checkInDate === viewDate && r.status === 'CONFIRMED')
    const listHasScrollbar = useHasOverflow(listRef, [dayReservations.length])

    return (
        <Modal onClose={onClose} size="lg">
            <h2 className="text-lg text-black font-semibold mb-2">Checking In</h2>

            <DateNavHeader viewDate={viewDate} onChange={handleDateChange} minDate={todayStr()} offsetToday={listHasScrollbar} />

            {dayReservations.length === 0 ? (
                <p className="text-sm text-muted text-center">No guests are checking in this day.</p>
            ) : (
                <ul ref={listRef} className="flex flex-col gap-3 max-h-96 overflow-y-auto">
                    {dayReservations.map(r => {
                        const guest = guestsMap[r.guestId]
                        const selected = selectedId === r.id

                        return (
                            <li key={r.id}>
                                <button
                                    type="button"
                                    onClick={() => setSelectedId(selected ? null : r.id)}
                                    className={`w-full bg-warm-white rounded flex flex-col gap-1 p-4 text-left ${selected ? 'border-2 border-black' : 'border-2 border-tan'}`}
                                >
                                    <span className="font-semibold text-black">{formatGuestName(guest)}</span>
                                    <p className="text-sm text-muted">{roomLabel(r)}</p>
                                </button>
                            </li>
                        )
                    })}
                </ul>
            )}

            <div className="flex justify-end gap-3 mt-4">
                <button type="button" onClick={onClose} className="btn btn-secondary">Close</button>
                <button
                    type="button"
                    onClick={() => onCheckIn(selectedId)}
                    className="btn btn-primary"
                    disabled={selectedId == null}
                >
                    Check In
                </button>
            </div>
        </Modal>
    )
}

export default CheckingInTodayModal
