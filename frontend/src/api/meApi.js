import api from './baseApi.js'

export function getCurrentUser() {
    return api.get('/me')
}

export function acknowledgeVersion() {
    return api.post('/me/acknowledge-version')
}
