import api from './baseApi.js'

export function getHousekeepingBoard() {
    return api.get('/housekeeping/board')
}

export function getHousekeepingStaff() {
    return api.get('/housekeeping/staff')
}

export function updateHousekeepingStatus(roomId, status) {
    return api.put(`/housekeeping/rooms/${roomId}/status`, { status })
}

export function assignHousekeeping(roomId, employeeId) {
    return api.put(`/housekeeping/rooms/${roomId}/assign`, { employeeId })
}
