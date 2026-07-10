import DoorCode from './DoorCode'

function DoorCodeModal({ reservationId, roomNumber, onClose }) {
    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
            <div className="bg-warm-white rounded-lg p-6 w-full max-w-sm shadow-lg border-t-4 border-rust">
                <h2 className="text-lg text-charcoal font-semibold mb-4">Room {roomNumber} — Door Code</h2>

                <DoorCode reservationId={reservationId} />

                <div className="flex justify-end mt-4">
                    <button onClick={onClose} className="btn btn-secondary">Close</button>
                </div>
            </div>
        </div>
    )
}

export default DoorCodeModal
