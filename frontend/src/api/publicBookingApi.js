import publicApi from './publicApi.js'

export function getRoomTypes() {
    return publicApi.get('/public/room-types')
}

export function getRates() {
    return publicApi.get('/public/rates')
}

export function getOccupiedDates(roomTypeId) {
    return publicApi.get(`/public/room-types/${roomTypeId}/occupied-dates`)
}

export function getAddOns(roomTypeId) {
    return publicApi.get('/public/add-ons', { params: { roomTypeId } })
}

export function getEstimate(rateType, guestCount, checkInDate, checkOutDate, roomTypeId, addOnIds) {
    return publicApi.get('/public/estimate', { params: { rateType, guestCount, checkInDate, checkOutDate, roomTypeId, addOnIds } })
}

export function createBooking(booking) {
    return publicApi.post('/public/bookings', booking)
}
