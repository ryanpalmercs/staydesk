import api from './baseApi.js'

export function getReservations() {
    return api.get('/reservations')
}

export function getReservation(id) {
    return api.get(`/reservations/${id}`)
}

export function createReservation(reservation) {
    return api.post(`/reservations`, reservation, {
        headers: { 'Content-Type': 'application/json'}
    })
}

export function updateReservation(id, reservation) {
    return api.put(`/reservations/${id}`, reservation)
}

export function deleteReservation(id) {
    return api.delete(`/reservations/${id}`)
}