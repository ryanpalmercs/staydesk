import { useEffect, useState } from 'react'
import { getActivityVersion } from '../api/activityApi'

const POLL_INTERVAL_MS = 20000

// `asOf` is the baseline timestamp the caller's own data was last fetched as-of. It must advance
// whenever that data is genuinely refetched (including via the caller's own actions, not just an
// explicit "Refresh" click here), or this would wrongly keep flagging the view as stale after the
// user's own edits.
function useStaleness(asOf) {
    const [latest, setLatest] = useState(null)
    const [dismissed, setDismissed] = useState(false)

    useEffect(() => {
        function poll() {
            getActivityVersion()
                .then(res => setLatest(res.data.asOf))
                .catch(err => console.error('Failed to poll activity version:', err))
        }

        poll()
        const interval = setInterval(poll, POLL_INTERVAL_MS)
        return () => clearInterval(interval)
    }, [])

    useEffect(() => { setDismissed(false) }, [asOf])

    const stale = asOf != null && latest != null && latest > asOf && !dismissed

    return { stale, dismiss: () => setDismissed(true) }
}

export default useStaleness
