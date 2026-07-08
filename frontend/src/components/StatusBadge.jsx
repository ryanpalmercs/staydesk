function StatusBadge({ status }) {
    const map = {
        AVAILABLE: ['bg-green-200 text-green-800', 'Available'],
        OCCUPIED: ['bg-rust/10 text-rust', 'Occupied'],
        MAINTENANCE: ['bg-amber-100 text-amber-800', 'Maintenance'],
        CONFIRMED: ['bg-tan text-brown', 'Confirmed'],
        CHECKED_IN: ['bg-green-100 text-green-800', 'Checked In'],
        CHECKED_OUT: ['bg-gray-100 text-gray-600', 'Checked Out'],
        CANCELLED: ['bg-gray-100 text-gray-500', 'Cancelled'],
        ACTIVE: ['bg-green-200 text-green-800', 'Active'],
        INACTIVE: ['bg-gray-100 text-gray-500', 'Inactive'],
        FLAGGED: ['bg-red-100 text-red-700', 'Flagged'],
        LEGAL_HOLD: ['bg-amber-100 text-amber-800', 'Legal Hold'],
    }

    const [cls, label] = map[status] ?? ['bg-tan text-muted', status]

    return <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${cls}`}>{label}</span>
}

export default StatusBadge