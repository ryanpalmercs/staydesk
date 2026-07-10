import api from './baseApi.js'

export function getRoomTypes() {
    return api.get('/room-types')
}

export function getRoomTypeOccupiedDates(roomTypeId) {
    return api.get(`/room-types/${roomTypeId}/occupied-dates`)
}
