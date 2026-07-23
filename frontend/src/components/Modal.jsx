import { useState } from "react"
import { useEscapeKey } from "../hooks/useEscapeKey"

const SIZE_CLASSES = {
    sm: "max-w-sm",
    md: "max-w-md",
    lg: "max-w-lg",
    xl: "max-w-2xl",
    wide: "max-w-md md:max-w-2xl lg:max-w-4xl",
    reservation: "max-w-md sm:max-w-3xl",
}

function Modal({ onClose, size = "md", scrollable = false, padded = true, isDirty = false, children }) {
    const [confirmingDiscard, setConfirmingDiscard] = useState(false)

    function requestClose() {
        if (confirmingDiscard) {
            setConfirmingDiscard(false)
        } else if (isDirty) {
            setConfirmingDiscard(true)
        } else {
            onClose()
        }
    }

    useEscapeKey(requestClose)

    const boxClasses = [
        "bg-warm-white rounded-lg shadow-lg border-t-4 border-rust w-full my-auto",
        SIZE_CLASSES[size],
        scrollable ? "max-h-[90vh] flex flex-col" : "",
        padded ? "p-6" : "",
    ].filter(Boolean).join(" ")

    return (
        <div
            className="fixed inset-0 bg-black/40 flex items-start justify-center overflow-y-auto z-50 py-8"
            onClick={requestClose}
        >
            <div className={boxClasses} onClick={e => e.stopPropagation()}>
                {children}
            </div>

            {confirmingDiscard && (
                <div
                    className="fixed inset-0 bg-black/40 flex items-center justify-center z-[60]"
                    onClick={() => setConfirmingDiscard(false)}
                >
                    <div
                        className="bg-warm-white rounded-lg p-6 w-full max-w-sm shadow-lg border-t-4 border-rust"
                        onClick={e => e.stopPropagation()}
                    >
                        <p className="text-sm text-black mb-4">Discard unsaved changes?</p>
                        <div className="flex justify-end gap-3">
                            <button type="button" onClick={() => setConfirmingDiscard(false)} className="btn btn-secondary">
                                Keep Editing
                            </button>
                            <button type="button" onClick={onClose} className="btn btn-primary">
                                Discard
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}

export default Modal
