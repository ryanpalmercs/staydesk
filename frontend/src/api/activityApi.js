import api from './baseApi.js'

export function getActivityVersion() {
    return api.get('/activity/version')
}
