import api from './baseApi'

export function getPendingIncidentCharges() {
    return api.get('/incident-charges/pending')
}

export function approveIncidentCharge(id) {
    return api.post(`/incident-charges/${id}/approve`)
}

export function rejectIncidentCharge(id, reason) {
    return api.post(`/incident-charges/${id}/reject`, { reason })
}