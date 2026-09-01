import api from './baseApi.js'

export function getReservations() {
    return api.get('/reservations')
}

export function getReservation(id) {
    return api.get(`/reservations/${id}`)
}

export function createReservation(reservation) {
    return api.post(`/reservations`, reservation)
}

export function updateReservation(id, reservation) {
    return api.put(`/reservations/${id}`, reservation)
}

export function deleteReservation(id) {
    return api.delete(`/reservations/${id}`)
}

export function getAvailableRoomsForCheckIn(id) {
    return api.get(`/reservations/${id}/available-rooms`)
}

export function checkIn(id, roomId, incidentalsPaymentMethodId, roomPaymentMethodId) {
    return api.post(`/reservations/${id}/check-in`, { roomId, incidentalsPaymentMethodId, roomPaymentMethodId })
}

export function checkInTerminal(id, roomId, posDeviceId = null) {
    return api.post(`/reservations/${id}/check-in/terminal`, { roomId, posDeviceId })
}

export function checkOut(id) {
    return api.post(`/reservations/${id}/check-out`)
}

export function cancelReservation(id) {
    return api.post(`/reservations/${id}/cancel`)
}

export function setReservationLegalHold(id) {
    return api.post(`/reservations/${id}/legal-hold`)
}

export function clearReservationLegalHold(id) {
    return api.delete(`/reservations/${id}/legal-hold`)
}

export function getReservationEstimate({ rateType, guestCount, checkInDate, checkOutDate }) {
    return api.get('/reservations/estimate', { params: { rateType, guestCount, checkInDate, checkOutDate } })
}

export function createMultiRoomReservation(payload) {
    return api.post('/reservations/multi', payload)
}

export function getUnsettledReservations() {
    return api.get('/reservations/unsettled')
}

export function backlogCheckIn(payload) {
    return api.post('/admin/reservations/backlog-check-in', payload)
}