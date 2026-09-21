import api from "./baseApi"

export function getFeatureFlags() {
    return api.get('/feature-flags')
}
