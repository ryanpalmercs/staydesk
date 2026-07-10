import { useEffect, useState } from 'react'
import { getActiveLockPasscode } from '../api/lockPasscodeApi'

function DoorCode({ reservationId }) {
    const [passcode, setPasscode] = useState(null)
    const [loading, setLoading] = useState(true)
    const [notFound, setNotFound] = useState(false)
    const [error, setError] = useState(false)

    useEffect(() => {
        getActiveLockPasscode(reservationId)
            .then(res => setPasscode(res.data))
            .catch(err => {
                if (err.response?.status === 404) {
                    setNotFound(true)
                } else {
                    setError(true)
                }
            })
            .finally(() => setLoading(false))
    }, [reservationId])

    if (loading) {
        return <p className="text-sm text-muted">Loading...</p>
    }

    if (notFound) {
        return <p className="text-sm text-muted">No active door code found for this reservation.</p>
    }

    if (error) {
        return <p className="text-sm text-rust">Failed to load the door code.</p>
    }

    return (
        <div className="text-center py-2">
            <p className="text-4xl font-semibold tracking-widest text-charcoal">{passcode.passcode}</p>
            <p className="text-xs text-muted mt-2">
                Valid until {new Date(passcode.endDate).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}
            </p>
        </div>
    )
}

export default DoorCode
