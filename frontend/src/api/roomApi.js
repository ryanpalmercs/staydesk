import api from './baseApi.js'

export function getRooms() {
    return api.get('/rooms')
}

export function getRoom(id) {
    return api.get(`/rooms/${id}`)
}

export function createRoom(room) {
    return api.post('/rooms', room)
}

export function updateRoom(id, room) {
    return api.put(`/rooms/${id}`, room)
}

export function deleteRoom(id) {
    return api.delete(`/rooms/${id}`)
}

export function getRoomAccessLog(id, days) {
    return api.get(`/admin/rooms/${id}/access-log`, { params: days ? { days } : {} })
}