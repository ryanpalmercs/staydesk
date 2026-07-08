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

export function flagGuest(id, reason) {
    return api.post(`/guests/${id}/flag`, { reason })
}

export function unflagGuest(id) {
    return api.delete(`/guests/${id}/flag`)
}

export function setGuestLegalHold(id) {
    return api.post(`/guests/${id}/legal-hold`)
}

export function clearGuestLegalHold(id) {
    return api.delete(`/guests/${id}/legal-hold`)
}