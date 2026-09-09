import api from './baseApi.js'

export function getRateOverrides() {
    return api.get('/rate-overrides')
}

export function createRateOverride(rateOverride) {
    return api.post('/rate-overrides', rateOverride)
}

export function updateRateOverride(id, rateOverride) {
    return api.put(`/rate-overrides/${id}`, rateOverride)
}

export function deleteRateOverride(id) {
    return api.delete(`/rate-overrides/${id}`)
}
