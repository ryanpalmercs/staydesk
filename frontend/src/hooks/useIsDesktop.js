import { useEffect, useState } from 'react'

const DESKTOP_BREAKPOINT = 640

export function useIsDesktop() {
    const [isDesktop, setIsDesktop] = useState(window.innerWidth >= DESKTOP_BREAKPOINT)

    useEffect(() => {
        function handleResize() {
            setIsDesktop(window.innerWidth >= DESKTOP_BREAKPOINT)
        }

        window.addEventListener('resize', handleResize)
        return () => window.removeEventListener('resize', handleResize)
    }, [])

    return isDesktop
}
