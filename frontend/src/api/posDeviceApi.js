import api from './baseApi.js'

export function getPosDevices() {
    return api.get('/pos-devices')
}

export function pairPosDevice(device) {
    return api.post('/pos-devices', device)
}

export function unpairPosDevice(id) {
    return api.delete(`/pos-devices/${id}`)
}

export function checkPosDeviceHealth(id) {
    return api.post(`/pos-devices/${id}/health-check`)
}