import api from './baseApi'

export function getReportSummary(startDate, endDate, comparisonStartDate, comparisonEndDate) {
    return api.get('/reports/summary', { params: { startDate, endDate, comparisonStartDate, comparisonEndDate } })
}

export function exportReport(report, format) {
    return api.post('/reports/export', report, { params: { format }, responseType: 'blob' })
}