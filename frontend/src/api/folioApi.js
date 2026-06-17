import api from './baseApi'

export function getFolioByReservationId(reservationId) {
    return api.get(`/folios/by-reservation/${reservationId}`)
}

export function getFolio(folioId) {
    return api.get(`/folios/${folioId}`)
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