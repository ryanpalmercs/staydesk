import api from "./baseApi"

export function getPropertySettings() {
    return api.get('/admin/settings/property')
}

export function updatePropertySetting(name, value) {
    return api.put(`/admin/settings/property/${name}`, null, { params: { value } })
}