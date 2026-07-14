import { useState } from 'react'

function DeleteReservationModal({ guest, onClose, onConfirm }) {
    const requiredText = guest ? `${guest.firstName} ${guest.lastName}` : 'DELETE'
    const [input, setInput] = useState('')
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    async function handleConfirm() {
        setSubmitting(true)
        setError(null)
        try {
            await onConfirm()
            onClose()
        } catch (err) {
            setError('Something went wrong.')
            setSubmitting(false)
        }
    }

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
            <div className="bg-warm-white rounded-lg p-6 w-full max-w-sm shadow-lg border-t-4 border-rust">
                <h2 className="text-lg text-charcoal font-semibold mb-4">Delete Reservation</h2>

                <p className="text-sm text-muted mb-3">
                    This permanently deletes the reservation and its folio/payment history. Type{' '}
                    <span className="font-semibold text-charcoal">{requiredText}</span> to confirm.
                </p>

                <input
                    autoFocus
                    value={input}
                    onChange={e => setInput(e.target.value)}
                    className="filter-input w-full mb-3"
                />

                {error && <p className="text-sm text-rust mb-3">{error}</p>}

                <div className="flex justify-end gap-2">
                    <button onClick={onClose} className="btn btn-secondary">Cancel</button>
                    <button
                        onClick={handleConfirm}
                        disabled={input !== requiredText || submitting}
                        className="btn btn-primary disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                        Delete
                    </button>
                </div>
            </div>
        </div>
    )
}

export default DeleteReservationModal