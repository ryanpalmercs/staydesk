import { renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useHasOverflow } from './useHasOverflow'

function refWith(scrollHeight, clientHeight) {
    return { current: { scrollHeight, clientHeight } }
}

describe('useHasOverflow', () => {
    it('is false when the ref has no current element', () => {
        const { result } = renderHook(() => useHasOverflow({ current: null }))
        expect(result.current).toBe(false)
    })

    it('is false when content fits without scrolling', () => {
        const ref = refWith(100, 100)
        const { result } = renderHook(() => useHasOverflow(ref))
        expect(result.current).toBe(false)
    })

    it('is true when scrollHeight exceeds clientHeight', () => {
        const ref = refWith(200, 100)
        const { result } = renderHook(() => useHasOverflow(ref))
        expect(result.current).toBe(true)
    })

    it('re-checks when a dep changes', () => {
        const ref = refWith(100, 100)
        const { result, rerender } = renderHook(({ deps }) => useHasOverflow(ref, deps), {
            initialProps: { deps: [1] }
        })
        expect(result.current).toBe(false)

        ref.current.scrollHeight = 300
        rerender({ deps: [2] })

        expect(result.current).toBe(true)
    })
})
