// Local-date arithmetic throughout -- new Date(isoString) parses as UTC midnight and
// toISOString() formats back in UTC, which reports tomorrow's date during evening hours
// in Central time (the property's timezone). new Date(y, m, d) and the local getters avoid
// UTC entirely.
export function toIsoDate(d) {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function parseIsoDate(iso) {
    const [y, m, d] = iso.split('-').map(Number)
    return new Date(y, m - 1, d)
}

export function todayIso() {
    return toIsoDate(new Date())
}

export function addDaysIso(iso, days) {
    const date = parseIsoDate(iso)
    date.setDate(date.getDate() + days)
    return toIsoDate(date)
}

export function datesInRange(checkInDate, checkOutDate) {
    const dates = []
    let cursor = parseIsoDate(checkInDate)
    const end = parseIsoDate(checkOutDate)
    while (cursor < end) {
        dates.push(toIsoDate(cursor))
        cursor.setDate(cursor.getDate() + 1)
    }
    return dates
}
