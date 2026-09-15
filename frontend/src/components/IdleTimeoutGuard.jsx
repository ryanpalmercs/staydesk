import { useAuth } from "../contexts/AuthContext"
import { useIdleTimeout } from "../hooks/useIdleTimeout"
import { supabase } from "../lib/supabase"
import Modal from "./Modal"

const IDLE_MINUTES = Number(import.meta.env.VITE_IDLE_TIMEOUT_MINUTES) || 30
const WARNING_SECONDS = 60

// Supabase's client auto-refreshes the access token forever as long as the tab stays open, so a
// forgotten-but-open tab never actually signs out on its own. This tracks real user activity
// (not just token freshness) and forces a sign-out after IDLE_MINUTES, warning for the last
// WARNING_SECONDS so an in-progress form isn't lost to someone stepping away briefly.
function IdleTimeoutGuard({ children }) {
    const { session } = useAuth()

    const { secondsLeft, stayActive } = useIdleTimeout({
        enabled: !!session,
        idleMinutes: IDLE_MINUTES,
        warningSeconds: WARNING_SECONDS,
        onTimeout: () => supabase.auth.signOut()
    })

    return (
        <>
            {children}

            {secondsLeft != null && (
                <Modal onClose={stayActive} size="sm">
                    <h2 className="text-lg text-black font-semibold mb-4">Still there?</h2>
                    <p className="text-sm text-muted mb-4">
                        You'll be signed out due to inactivity in {secondsLeft}s.
                    </p>
                    <div className="flex justify-end gap-3">
                        <button type="button" onClick={() => supabase.auth.signOut()} className="btn btn-secondary">
                            Sign Out Now
                        </button>
                        <button type="button" onClick={stayActive} className="btn btn-primary">
                            Stay Signed In
                        </button>
                    </div>
                </Modal>
            )}
        </>
    )
}

export default IdleTimeoutGuard
