import api from './baseApi'

export function getSifelyStatus() {
    return api.get('/admin/sifely/status')
}

export function connectSifely(payload) {
    return api.post('/admin/sifely/connect', payload)
}

export function disconnectSifely() {
    return api.delete('/admin/sifely/connect')
}

export function getSifelyLocks() {
    return api.get('/admin/sifely/locks')
}
