import api from "./baseApi"

export function syncEmployeesToQuickBooks() {
    return api.post('/admin/payroll/sync-employees')
}