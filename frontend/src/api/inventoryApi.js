import api from './baseApi.js'

export function getInventoryBoard() {
    return api.get('/inventory/items')
}

export function getLowStockItems() {
    return api.get('/inventory/low-stock')
}

export function createInventoryItem(name, category, unit, lowStockThreshold) {
    return api.post('/inventory/items', { name, category, unit, lowStockThreshold })
}

export function updateInventoryItem(itemId, name, category, unit, lowStockThreshold, active) {
    return api.put(`/inventory/items/${itemId}`, { name, category, unit, lowStockThreshold, active })
}

export function restockItem(itemId, quantity, note) {
    return api.post(`/inventory/items/${itemId}/restock`, { quantity, note })
}

export function logUsage(itemId, quantity, roomId, note) {
    return api.post(`/inventory/items/${itemId}/usage`, { quantity, roomId, note })
}
