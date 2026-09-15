import { useState } from "react"
import Modal from "./Modal"

function CheckingInTodayModal({ reservations, guestsMap, roomLabel, onClose, onCheckIn }) {
    const [selectedId, setSelectedId] = useState(null)

    return (
        <Modal onClose={onClose} size="lg">
            <h2 className="text-lg text-black font-semibold mb-4">Checking In Today</h2>

            {reservations.length === 0 ? (
                <p className="text-sm text-muted">No guests are checking in today.</p>
            ) : (
                <ul className="flex flex-col gap-3 max-h-96 overflow-y-auto">
                    {reservations.map(r => {
                        const guest = guestsMap[r.guestId]
                        const selected = selectedId === r.id

                        return (
                            <li key={r.id}>
                                <button
                                    type="button"
                                    onClick={() => setSelectedId(selected ? null : r.id)}
                                    className={`w-full bg-warm-white rounded flex flex-col gap-1 p-4 text-left ${selected ? 'border-2 border-black' : 'border-2 border-tan'}`}
                                >
                                    <span className="font-semibold text-black">{guest?.firstName} {guest?.lastName}</span>
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
