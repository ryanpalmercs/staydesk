import api from './baseApi.js'

export function getGuests() {
    return api.get('/guests')
}

export function getGuest(id) {
    return api.get(`/guests/${id}`)
}

export function createGuest(guest) {
    return api.post('/guests', guest)
}

export function updateGuest(id, guest) {
    return api.put(`/guests/${id}`, guest)
}