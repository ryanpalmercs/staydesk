import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useIdleTimeout } from './useIdleTimeout'

describe('useIdleTimeout', () => {
    beforeEach(() => {
        vi.useFakeTimers()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it('does nothing when disabled', () => {
        const onTimeout = vi.fn()
        renderHook(() => useIdleTimeout({ enabled: false, idleMinutes: 1, warningSeconds: 10, onTimeout }))

        act(() => vi.advanceTimersByTime(10 * 60 * 1000))

        expect(onTimeout).not.toHaveBeenCalled()
    })

    it('shows a countdown during the warning window, then calls onTimeout', () => {
        const onTimeout = vi.fn()
        const { result } = renderHook(() =>
            useIdleTimeout({ enabled: true, idleMinutes: 1, warningSeconds: 10, onTimeout })
        )

        expect(result.current.secondsLeft).toBe(null)

        // idle for 50s (idleMinutes*60 - warningSeconds) to enter the warning window
        act(() => vi.advanceTimersByTime(50 * 1000))
        expect(result.current.secondsLeft).toBe(10)

        act(() => vi.advanceTimersByTime(5 * 1000))
        expect(result.current.secondsLeft).toBe(5)
        expect(onTimeout).not.toHaveBeenCalled()

        act(() => vi.advanceTimersByTime(5 * 1000))
        expect(onTimeout).toHaveBeenCalledTimes(1)
    })

    it('passive activity resets the countdown before the warning shows', () => {
        const onTimeout = vi.fn()
        const { result } = renderHook(() =>
            useIdleTimeout({ enabled: true, idleMinutes: 1, warningSeconds: 10, onTimeout })
        )

        act(() => vi.advanceTimersByTime(40 * 1000))
        act(() => window.dispatchEvent(new Event('keydown')))
        act(() => vi.advanceTimersByTime(40 * 1000))

        // Would have already fired the warning at 50s from the original start if activity hadn't
        // reset the clock - 80s total elapsed here, only 40s since the reset.
        expect(result.current.secondsLeft).toBe(null)
        expect(onTimeout).not.toHaveBeenCalled()
    })

    it('ignores passive activity once the warning is already showing', () => {
        const onTimeout = vi.fn()
        const { result } = renderHook(() =>
            useIdleTimeout({ enabled: true, idleMinutes: 1, warningSeconds: 10, onTimeout })
        )

        act(() => vi.advanceTimersByTime(55 * 1000))
        expect(result.current.secondsLeft).toBe(5)

        act(() => window.dispatchEvent(new Event('keydown')))
        expect(result.current.secondsLeft).toBe(5)

        act(() => vi.advanceTimersByTime(5 * 1000))
        expect(onTimeout).toHaveBeenCalledTimes(1)
    })

    it('stayActive explicitly resets the timer even during the warning', () => {
        const onTimeout = vi.fn()
        const { result } = renderHook(() =>
            useIdleTimeout({ enabled: true, idleMinutes: 1, warningSeconds: 10, onTimeout })
        )

        act(() => vi.advanceTimersByTime(55 * 1000))
        expect(result.current.secondsLeft).toBe(5)

        act(() => result.current.stayActive())
        expect(result.current.secondsLeft).toBe(null)

        act(() => vi.advanceTimersByTime(55 * 1000))
        expect(onTimeout).not.toHaveBeenCalled()
        expect(result.current.secondsLeft).toBe(5)
    })
})
