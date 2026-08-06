function ConfirmDialog({ message, cancelLabel = 'Cancel', confirmLabel = 'Confirm', onCancel, onConfirm }) {
    return (
        <div
            className="fixed inset-0 bg-black/40 flex items-center justify-center z-[60]"
            onClick={onCancel}
        >
            <div
                className="bg-warm-white rounded-lg p-6 w-full max-w-sm shadow-lg border-t-4 border-rust"
                onClick={e => e.stopPropagation()}
            >
                <p className="text-sm text-black mb-4">{message}</p>
                <div className="flex justify-end gap-3">
                    <button type="button" onClick={onCancel} className="btn btn-secondary">
                        {cancelLabel}
                    </button>
                    <button type="button" onClick={onConfirm} className="btn btn-primary">
                        {confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    )
}

export default ConfirmDialog
