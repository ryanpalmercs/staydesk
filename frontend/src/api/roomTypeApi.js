import api from './baseApi.js'

export function getRoomTypes() {
    return api.get('/room-types')
}

export function getRoomTypeOccupiedDates(roomTypeId, excludeReservationId) {
    return api.get(`/room-types/${roomTypeId}/occupied-dates`, { params: { excludeReservationId } })
}

export function updateRoomType(id, roomType) {
    return api.put(`/room-types/${id}`, roomType)
}
