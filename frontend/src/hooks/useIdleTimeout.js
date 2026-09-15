import { useCallback, useEffect, useRef, useState } from "react"

// Deliberately excludes mousemove - just resting/bumping the mouse isn't "activity," only an
// actual click, keypress, scroll, or touch counts toward resetting the idle clock.
const ACTIVITY_EVENTS = ["mousedown", "keydown", "scroll", "touchstart"]

// Fires onTimeout after idleMinutes of no tracked activity, surfacing secondsLeft during the final
// warningSeconds so a caller can show a "still there?" prompt before actually signing out.
export function useIdleTimeout({ enabled, idleMinutes, warningSeconds, onTimeout }) {
    const [secondsLeft, setSecondsLeft] = useState(null)
    const warningTimerRef = useRef(null)
    const logoutTimerRef = useRef(null)
    const countdownIntervalRef = useRef(null)
    const inWarningRef = useRef(false)
    const onTimeoutRef = useRef(onTimeout)
    onTimeoutRef.current = onTimeout

    const clearTimers = useCallback(() => {
        clearTimeout(warningTimerRef.current)
        clearTimeout(logoutTimerRef.current)
        clearInterval(countdownIntervalRef.current)
    }, [])

    // Always reschedules, regardless of whether the warning is currently showing - this is what
    // the explicit "Stay Signed In" action calls.
    const scheduleTimers = useCallback(() => {
        clearTimers()
        inWarningRef.current = false
        setSecondsLeft(null)

        if (!enabled) {
            return
        }

        const idleMs = idleMinutes * 60 * 1000
        const warningMs = warningSeconds * 1000

        warningTimerRef.current = setTimeout(() => {
            inWarningRef.current = true
            setSecondsLeft(warningSeconds)
            countdownIntervalRef.current = setInterval(() => {
                setSecondsLeft(s => (s == null ? null : Math.max(0, s - 1)))
            }, 1000)
        }, Math.max(0, idleMs - warningMs))

        logoutTimerRef.current = setTimeout(() => {
            clearTimers()
            onTimeoutRef.current()
        }, idleMs)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [enabled, idleMinutes, warningSeconds, clearTimers])

    // Passive activity only resets the pre-warning countdown. Once the "still there?" prompt is up,
    // incidental movement (a mouse resting on the desk, a jostled table) shouldn't silently dismiss
    // it - only the explicit Stay Signed In action (which calls scheduleTimers directly) should.
    const handleActivity = useCallback(() => {
        if (inWarningRef.current) {
            return
        }
        scheduleTimers()
    }, [scheduleTimers])

    useEffect(() => {
        scheduleTimers()

        if (!enabled) {
            return
        }

        ACTIVITY_EVENTS.forEach(evt => window.addEventListener(evt, handleActivity))
        return () => {
            ACTIVITY_EVENTS.forEach(evt => window.removeEventListener(evt, handleActivity))
            clearTimers()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [enabled])

    return { secondsLeft, stayActive: scheduleTimers }
}
