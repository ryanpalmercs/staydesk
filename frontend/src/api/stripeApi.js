import api from './baseApi'

export function getConnectStatus() {
    return api.get('/stripe/connect/status')
}

export function disconnectStripe() {
    return api.delete('/stripe/connect')
}

export function connectStripe() {
    return api.get('/stripe/connect')
}
