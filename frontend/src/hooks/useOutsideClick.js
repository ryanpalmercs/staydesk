import { useEffect } from "react"

export function useOutsideClick(ref, onOutsideClick) {
    useEffect(() => {
        function handleClick(e) {
            if (ref.current && !ref.current.contains(e.target)) onOutsideClick()
        }
        document.addEventListener("mousedown", handleClick)
        return () => document.removeEventListener("mousedown", handleClick)
    }, [ref, onOutsideClick])
}
