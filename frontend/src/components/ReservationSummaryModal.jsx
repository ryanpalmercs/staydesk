import StatusBadge from './StatusBadge'
import Modal from './Modal'
import { formatGuestName } from '../utils/guestName'

function formatDate(str) {
    return new Date(str + 'T12:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function ReservationSummaryModal({ reservation, guest, roomLabel, onClose, onCheckOut, onCheckIn, onViewFolio, onExtend, onAssignRoom, onMoveRoom }) {
    return (
        <Modal onClose={onClose} size="lg-xl">
            <div className="flex items-start justify-between mb-4">
                <div>
                    <h2 className="text-lg font-semibold text-black">
                        {formatGuestName(guest)}
                    </h2>
                    <p className="text-sm text-muted">{roomLabel}</p>
                </div>
                <StatusBadge status={reservation.status} />
            </div>

            <div className="flex justify-center gap-3">
                <p className="text-sm text-black mb-1">
                    {formatDate(reservation.checkInDate)} → {formatDate(reservation.checkOutDate)}
                </p>

                {reservation.confirmationCode && (
                    <p className="text-sm text-muted mb-6">Confirmation #{reservation.confirmationCode}</p>
                )}
            </div>

            <div className="flex justify-center gap-3">
                <button onClick={onClose} className="btn btn-secondary">Close</button>
                {reservation.status === 'CONFIRMED' && (
                    <>
                        {reservation.roomId == null && (
                            <button onClick={onAssignRoom} className="btn btn-secondary">Assign Room</button>
                        )}
                        <button onClick={onCheckIn} className="btn btn-primary">Check In</button>
                    </>
                )}
                {reservation.status === 'CHECKED_IN' && (
                    <>
                        <button onClick={onViewFolio} className="btn btn-secondary">View Folio</button>
                        <button onClick={onExtend} className="btn btn-secondary">Extend Stay</button>
                        <button onClick={onMoveRoom} className="btn btn-secondary">Move Room</button>
                        <button onClick={onCheckOut} className="btn btn-primary">Check Out</button>
                    </>
                )}
            </div>
        </Modal>
    )
}

export default ReservationSummaryModal