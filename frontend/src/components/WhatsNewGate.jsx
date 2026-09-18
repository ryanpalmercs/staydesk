import { useEffect, useState } from "react"
import { getCurrentUser, acknowledgeVersion } from "../api/meApi"
import { releaseNotes } from "../data/releaseNotes"
import WhatsNewModal from "./WhatsNewModal"

const RELOADED_FOR_KEY = 'staydesk-reloaded-for-version'

// Runs once per authenticated session. Two unrelated comparisons:
// - our own running bundle (__APP_VERSION__, baked in at build time) vs. the backend's live
//   currentAppVersion (a raw git SHA) - if we're behind, our JS is stale-cached, so force one
//   reload to pick up the fresh deploy. Guarded by sessionStorage so a deploy that never fully
//   propagates can't trap someone in a reload loop.
// - the employee/account's own last_seen_release_notes_id vs. releaseNotes.js's entries - shows
//   every entry newer than what they've seen, not just the latest, so someone who logs in rarely
//   doesn't miss what shipped in between. A raw deploy SHA can't drive this: most deploys don't
//   add a release note, so entries are tracked by their own small hand-assigned id instead.
function WhatsNewGate() {
    const [pending, setPending] = useState(null)

    useEffect(() => {
        getCurrentUser().then(res => {
            const { lastSeenReleaseNotesId, currentAppVersion } = res.data

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

            const latest = releaseNotes[0]
            if (!latest) {
                return
            }

            const missed = lastSeenReleaseNotesId == null
                ? [latest]
                : releaseNotes.filter(entry => entry.id > lastSeenReleaseNotesId)

            if (missed.length > 0) {
                setPending(missed)
            }
        }).catch(() => {})
    }, [])

    function handleAcknowledge() {
        acknowledgeVersion(releaseNotes[0].id).finally(() => setPending(null))
    }

    if (!pending) {
        return null
    }

    return <WhatsNewModal entries={pending} onAcknowledge={handleAcknowledge} />
}

export default WhatsNewGate
