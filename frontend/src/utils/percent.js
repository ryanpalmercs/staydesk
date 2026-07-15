export function parsePercent(value) {
    const num = parseFloat(value)
    return isNaN(num) ? '' : (num / 100).toString()
}

export function formatPercent(value) {
    const num = parseFloat(value)
    return isNaN(num) ? '' : (num * 100).toFixed(2)
}

export function displayPercent(value) {
    const num = parseFloat(value)
    return isNaN(num) ? '' : num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + '%'
}