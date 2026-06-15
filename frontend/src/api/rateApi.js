import api from './baseApi.js'

export function getRates() {
    return api.get('/rates')
}

export function updateRate(id, rate) {
    return api.put(`/rates/${id}`, rate)
}