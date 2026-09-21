import { useRef, useState } from "react"
import Modal from "./Modal"
import DateNavHeader, { todayStr } from "./DateNavHeader"
import { formatGuestName } from "../utils/guestName"
import { useHasOverflow } from "../hooks/useHasOverflow"

function CheckingOutTodayModal({ reservations, guestsMap, roomLabel, onClose, onCheckOut, onExtend }) {
    const [viewDate, setViewDate] = useState(todayStr)
    const [selectedId, setSelectedId] = useState(null)
    const [submitting, setSubmitting] = useState(false)
    const listRef = useRef(null)

    function handleDateChange(date) {
        setViewDate(date)
        setSelectedId(null)
    }

    async function handleCheckOut() {
        setSubmitting(true)
        try {
            await onCheckOut(selectedId)
        } finally {
            setSubmitting(false)
        }
    }

    const dayReservations = reservations.filter(r => r.checkOutDate === viewDate && r.status === 'CHECKED_IN')
    const listHasScrollbar = useHasOverflow(listRef, [dayReservations.length])

    return (
        <Modal onClose={onClose} size="lg">
            <h2 className="text-lg text-black font-semibold mb-2">Checking Out</h2>

            <DateNavHeader viewDate={viewDate} onChange={handleDateChange} minDate={todayStr()} offsetToday={listHasScrollbar} />

            {dayReservations.length === 0 ? (
                <p className="text-sm text-muted text-center">No guests are checking out this day.</p>
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
                    onClick={() => onExtend(selectedId)}
                    className="btn btn-secondary"
                    disabled={selectedId == null || submitting}
                >
                    Extend Stay
                </button>
                <button
                    type="button"
                    onClick={handleCheckOut}
                    className="btn btn-primary"
                    disabled={selectedId == null || submitting}
                >
                    Check Out
                </button>
            </div>
        </Modal>
    )
}

export default CheckingOutTodayModal
