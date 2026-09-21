import { useEffect, useState } from "react"

// True once the ref'd element's content is actually taller than it (i.e. it's showing a
// scrollbar). Re-checks whenever a value in deps changes, since resizing to fit new content
// happens synchronously before paint, but the check itself needs to run after that layout.
export function useHasOverflow(ref, deps = []) {
    const [hasOverflow, setHasOverflow] = useState(false)

    useEffect(() => {
        setHasOverflow(!!ref.current && ref.current.scrollHeight > ref.current.clientHeight)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, deps)

    return hasOverflow
}
