import api from './baseApi'

export function getQuickBooksStatus() {
    return api.get('/admin/quickbooks/status')
}

export async function startQuickBooksConnect() {
    const res = await api.get('/admin/quickbooks/connect')
    window.location.href = res.data.authorizeUrl
}

export function disconnectQuickBooks() {
    return api.delete('/admin/quickbooks/connect')
}