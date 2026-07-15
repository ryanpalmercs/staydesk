import { useEffect, useState } from 'react'
import { getUnacknowledgedLockPasscodes, acknowledgeLockPasscode } from '../api/lockPasscodeApi'

const POLL_INTERVAL_MS = 60000

export default function DoorAccessToast() {
    const [notifications, setNotifications] = useState([])

    useEffect(() => {
        function poll() {
            getUnacknowledgedLockPasscodes()
                .then(res => setNotifications(res.data))
                .catch(err => console.error('Failed to poll door access notifications:', err))
        }

        poll()
        const interval = setInterval(poll, POLL_INTERVAL_MS)
        return () => clearInterval(interval)
    }, [])

    function handleDismiss(id) {
        setNotifications(prev => prev.filter(n => n.lockPasscodeId !== id))
        acknowledgeLockPasscode(id).catch(err => console.error('Failed to acknowledge door access notification:', err))
    }

    if (notifications.length === 0) {
        return null
    }

    return (
        <div className="fixed top-4 right-4 z-50 flex flex-col gap-2 max-w-sm">
            {notifications.map(n => (
                <div key={n.lockPasscodeId} className="bg-warm-white border-l-4 border-green-600 rounded-lg shadow-lg p-4">
                    <p className="text-sm font-medium text-black">Door code issued — Room {n.roomNumber}</p>
                    <p className="text-2xl font-semibold tracking-widest text-black mt-2">{n.passcode}</p>
                    <p className="text-xs text-muted mt-1">
                        Resolved {new Date(n.resolvedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </p>
                    <div className="flex justify-end mt-2">
                        <button
                            onClick={() => handleDismiss(n.lockPasscodeId)}
                            className="text-sm font-medium text-green hover:text-black"
                        >
                            Dismiss
                        </button>
                    </div>
                </div>
            ))}
        </div>
    )
}
