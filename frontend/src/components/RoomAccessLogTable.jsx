import './RoomAccessLogTable.css'

const ACTOR_STYLES = {
    GUEST: 'bg-green-100 text-green-800',
    STAFF: 'bg-tan text-brown',
    UNKNOWN: 'bg-gray-100 text-gray-700',
    UNAUTHORIZED: 'bg-rust/10 text-rust',
}

function formatEventTime(isoString) {
    return new Date(isoString).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function RoomAccessLogTable({ events }) {
    if (events.length === 0) {
        return <p className="text-sm text-muted">No access events in this window.</p>
    }

    return (
        <table className="access-log-table">
            <thead>
                <tr>
                    <th>Time</th>
                    <th>Event</th>
                    <th>Who</th>
                    <th>Result</th>
                </tr>
            </thead>
            <tbody>
                {events.map((event, i) => (
                    <tr key={i}>
                        <td>{formatEventTime(event.occurredAt)}</td>
                        <td>{event.eventType}</td>
                        <td>
                            <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${ACTOR_STYLES[event.actorType] ?? ACTOR_STYLES.UNKNOWN}`}>
                                {event.actor}
                            </span>
                        </td>
                        <td>{event.success ? 'Success' : 'Failed'}</td>
                    </tr>
                ))}
            </tbody>
        </table>
    )
}

export default RoomAccessLogTable