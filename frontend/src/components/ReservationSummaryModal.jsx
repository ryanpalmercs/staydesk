import StatusBadge from './StatusBadge'

function formatDate(str) {
    return new Date(str + 'T12:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function ReservationSummaryModal({ reservation, guest, roomLabel, onClose, onCheckOut, onCheckIn, onViewFolio }) {
    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
            <div className="bg-warm-white rounded-lg p-6 w-full max-w-md shadow-lg border-t-4 border-rust" onClick={e => e.stopPropagation()}>
                <div className="flex items-start justify-between mb-4">
                    <div>
                        <h2 className="text-lg font-semibold text-charcoal">
                            {guest?.firstName} {guest?.lastName}
                        </h2>
                        <p className="text-sm text-muted">{roomLabel}</p>
                    </div>
                    <StatusBadge status={reservation.status} />
                </div>

                <p className="text-sm text-charcoal mb-1">
                    {formatDate(reservation.checkInDate)} → {formatDate(reservation.checkOutDate)}
                </p>

                {reservation.confirmationCode && (
                    <p className="text-sm text-muted mb-6">Confirmation #{reservation.confirmationCode}</p>
                )}

                <div className="flex justify-end gap-3">
                    <button onClick={onClose} className="btn btn-secondary">Close</button>
                    {reservation.status === 'CONFIRMED' && (
                        <button onClick={onCheckIn} className="btn btn-primary">Check In</button>
                    )}
                    {reservation.status === 'CHECKED_IN' && (
                        <>
                            <button onClick={onViewFolio} className="btn btn-secondary">View Folio</button>
                            <button onClick={onCheckOut} className="btn btn-primary">Check Out</button>
                        </>
                    )}
                </div>
            </div>
        </div>
    )
}

export default ReservationSummaryModal