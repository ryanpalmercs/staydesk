import api from './baseApi.js'

export function clockIn(employeeId, notes) {
    return api.post(`/employees/${employeeId}/clock-in`, { notes })
}

export function clockOut(employeeId, notes) {
    return api.post(`/employees/${employeeId}/clock-out`, { notes })
}

export function getTimesheet(employeeId, start, end) {
    return api.get(`/employees/${employeeId}/time-entries`, { params: { start, end } })
}

export function getPayPeriodEntries(start, end) {
    return api.get('/admin/time-entries', { params: { start, end } })
}

export function createManualEntry(entry) {
    return api.post('/admin/time-entries', entry)
}

export function updateTimeEntry(id, entry) {
    return api.put(`/admin/time-entries/${id}`, entry)
}

export function deleteTimeEntry(id) {
    return api.delete(`/admin/time-entries/${id}`)
}

export function exportTimesheets(startDate, endDate, format) {
    return api.get('/admin/timesheets/export', {
        params: { startDate, endDate, format },
        responseType: 'blob'
    })
}