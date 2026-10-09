import { renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useEscapeKey } from './useEscapeKey'

function pressKey(key) {
    document.dispatchEvent(new KeyboardEvent('keydown', { key }))
}

describe('useEscapeKey', () => {
    it('calls the callback when Escape is pressed', () => {
        const onEscape = vi.fn()
        renderHook(() => useEscapeKey(onEscape))

        pressKey('Escape')

        expect(onEscape).toHaveBeenCalledTimes(1)
    })

    it('does not call the callback for other keys', () => {
        const onEscape = vi.fn()
        renderHook(() => useEscapeKey(onEscape))

        pressKey('Enter')

        expect(onEscape).not.toHaveBeenCalled()
    })

    it('stops listening after unmount', () => {
        const onEscape = vi.fn()
        const { unmount } = renderHook(() => useEscapeKey(onEscape))

        unmount()
        pressKey('Escape')

        expect(onEscape).not.toHaveBeenCalled()
    })
})
