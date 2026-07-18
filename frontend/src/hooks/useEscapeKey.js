import { useEffect } from "react"

export function useEscapeKey(onEscape) {
    useEffect(() => {
        function handleKeyDown(e) {
            if (e.key === "Escape") onEscape()
        }
        document.addEventListener("keydown", handleKeyDown)
        return () => document.removeEventListener("keydown", handleKeyDown)
    }, [onEscape])
}
