const INACTIVE_STATUSES = ['CANCELLED', 'CHECKED_OUT', 'NO_SHOW']

// Mirrors the backend's room-level conflict check for the minimal one-night extension
// (ReservationService.prepareExtendStay's findOverlapping call) - true if another active
// reservation on the same room already claims the day right after this one's check-out date,
// meaning no extension (not even by one night) could possibly succeed.
export function isExtendBlocked(reservation, reservations) {
    if (!reservation || reservation.roomId == null) return false

    return reservations.some(r =>
        r.id !== reservation.id &&
        r.roomId === reservation.roomId &&
        !INACTIVE_STATUSES.includes(r.status) &&
        r.checkInDate <= reservation.checkOutDate &&
        r.checkOutDate > reservation.checkOutDate
    )
}

export const EXTEND_BLOCKED_MESSAGE = 'Another reservation already starts in this room on the check-out date, so this stay can\'t be extended.'
