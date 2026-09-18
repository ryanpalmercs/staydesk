import { useEffect, useState } from "react"
import { getCurrentUser, acknowledgeVersion } from "../api/meApi"
import WhatsNewModal from "./WhatsNewModal"

const RELOADED_FOR_KEY = 'staydesk-reloaded-for-version'

// Runs once per authenticated session. Two separate comparisons:
// - our own running bundle (__APP_VERSION__, baked in at build time) vs. the backend's live
//   currentAppVersion - if we're behind, our JS is stale-cached, so force one reload to pick up
//   the fresh deploy. Guarded by sessionStorage so a deploy that never fully propagates can't
//   trap someone in a reload loop.
// - the employee/account's own last_seen_app_version vs. currentAppVersion - if they haven't
//   seen this version yet, show the release notes, then acknowledge.
function WhatsNewGate() {
    const [pending, setPending] = useState(false)

    useEffect(() => {
        getCurrentUser().then(res => {
            const { lastSeenAppVersion, currentAppVersion } = res.data

            let reloadedFor = null
            try {
                reloadedFor = sessionStorage.getItem(RELOADED_FOR_KEY)
            } catch {
                // private browsing / blocked storage - fall through, worst case we skip the forced reload
            }

            if (__APP_VERSION__ !== currentAppVersion && reloadedFor !== currentAppVersion) {
                try {
                    sessionStorage.setItem(RELOADED_FOR_KEY, currentAppVersion)
                } catch {
                    // ignore - see above
                }
                window.location.reload()
                return
            }

            if (lastSeenAppVersion !== currentAppVersion) {
                setPending(true)
            }
        }).catch(() => {})
    }, [])

    function handleAcknowledge() {
        acknowledgeVersion().finally(() => setPending(false))
    }

    if (!pending) {
        return null
    }

    return <WhatsNewModal onAcknowledge={handleAcknowledge} />
}

export default WhatsNewGate
