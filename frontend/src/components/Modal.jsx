import { useState } from "react"
import { useEscapeKey } from "../hooks/useEscapeKey"
import ConfirmDialog from "./ConfirmDialog"

const SIZE_CLASSES = {
    sm: "max-w-sm",
    md: "max-w-md",
    lg: "max-w-lg",
    "lg-xl": "max-w-xl",
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
                <ConfirmDialog
                    message="Discard unsaved changes?"
                    cancelLabel="Keep Editing"
                    confirmLabel="Discard"
                    onCancel={() => setConfirmingDiscard(false)}
                    onConfirm={onClose}
                />
            )}
        </div>
    )
}

export default Modal
