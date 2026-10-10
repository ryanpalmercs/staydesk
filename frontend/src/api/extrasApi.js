import api from './baseApi'

export function getExtras() {
    return api.get('/extras')
}

export function getAllExtras() {
    return api.get('/extras/all')
}

export function createExtra(extra) {
    return api.post('/extras', extra)
}

export function updateExtra(id, extra) {
    return api.put(`/extras/${id}`, extra)
}

export function deleteExtra(id) {
    return api.delete(`/extras/${id}`)
}
