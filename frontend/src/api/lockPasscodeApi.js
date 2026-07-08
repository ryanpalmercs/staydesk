import api from './baseApi.js'

export function getUnacknowledgedLockPasscodes() {
    return api.get('/lock-passcodes/unacknowledged')
}

export function acknowledgeLockPasscode(id) {
    return api.put(`/lock-passcodes/${id}/acknowledge`)
}
