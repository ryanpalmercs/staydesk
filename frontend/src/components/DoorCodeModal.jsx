import DoorCode from './DoorCode'
import Modal from './Modal'

function DoorCodeModal({ reservationId, roomNumber, onClose }) {
    return (
        <Modal onClose={onClose} size="sm">
            <h2 className="text-lg text-black font-semibold mb-4">Room {roomNumber} — Door Code</h2>

            <DoorCode reservationId={reservationId} />

            <div className="flex justify-end mt-4">
                <button onClick={onClose} className="btn btn-secondary">Close</button>
            </div>
        </Modal>
    )
}

export default DoorCodeModal
