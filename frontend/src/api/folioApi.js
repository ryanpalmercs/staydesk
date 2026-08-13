import api from './baseApi'

export function getFolioByReservationId(reservationId) {
    return api.get(`/folios/by-reservation/${reservationId}`)
}

export function getFolio(folioId) {
    return api.get(`/folios/${folioId}`)
}

export function getFolioPayments(folioId) {
    return api.get(`/folios/${folioId}/payments`)
}

export function getFolioItems(folioId) {
    return api.get(`/folios/${folioId}/items`)
}

export function addFolioItem(folioId, extraId, quantity) {
    return api.post(`/folios/${folioId}/items`, { extraId, quantity })
}

export function payFolio(folioId) {
    return api.post(`/folios/${folioId}/pay`)
}

export function requestIncidentCharge(folioId, amount, reason) {
    return api.post(`/folios/${folioId}/incident-charges`, { amount, reason })
}

export function getFolioIncidentCharges(folioId) {
    return api.get(`/folios/${folioId}/incident-charges`)
}

export function settleWalkInStay(folioId, roomPaymentMethodId) {
    return api.post(`/folios/${folioId}/settle-stay`, { roomPaymentMethodId })
}

export function settleWalkInStayTerminal(folioId, posDeviceId) {
    return api.post(`/folios/${folioId}/settle-stay/terminal`, { posDeviceId })
}