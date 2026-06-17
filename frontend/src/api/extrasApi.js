import api from './baseApi'

export function getExtras() {
    return api.get('/extras')
}
