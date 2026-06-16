import api from './baseApi'

export function getFolioByReservationId(reservationId) {
    return api.get(`/folios/by-reservation/${reservationId}`)
}

export function payFolio(folioId) {
    return api.post(`/folios/${folioId}/pay`)
}