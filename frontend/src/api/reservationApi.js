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

export function checkIn(id) {
    return api.post(`/reservations/${id}/check-in`)
}

export function checkOut(id) {
    return api.post(`/reservations/${id}/check-out`)
}

export function cancelReservation(id) {
    return api.post(`/reservations/${id}/cancel`)
}