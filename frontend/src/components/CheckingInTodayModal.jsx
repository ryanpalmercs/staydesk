import { useState } from "react"
import { addDays, format } from "date-fns"
import { ArrowLeftIcon, ArrowRightIcon } from "lucide-react"
import Modal from "./Modal"
import { formatGuestName } from "../utils/guestName"

function todayStr() {
    return format(new Date(), 'yyyy-MM-dd')
}

function formatDateLabel(dateStr) {
    const label = new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' })
    return dateStr === todayStr() ? `${label} (Today)` : label
}

function CheckingInTodayModal({ reservations, guestsMap, roomLabel, onClose, onCheckIn }) {
    const [viewDate, setViewDate] = useState(todayStr)
    const [selectedId, setSelectedId] = useState(null)

    function shiftDate(deltaDays) {
        setViewDate(d => format(addDays(new Date(d + 'T12:00:00'), deltaDays), 'yyyy-MM-dd'))
        setSelectedId(null)
    }

    const dayReservations = reservations.filter(r => r.checkInDate === viewDate && r.status === 'CONFIRMED')

    return (
        <Modal onClose={onClose} size="lg">
            <h2 className="text-lg text-black font-semibold mb-2">Checking In</h2>

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

            {dayReservations.length === 0 ? (
                <p className="text-sm text-muted">No guests are checking in this day.</p>
            ) : (
                <ul className="flex flex-col gap-3 max-h-96 overflow-y-auto">
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
