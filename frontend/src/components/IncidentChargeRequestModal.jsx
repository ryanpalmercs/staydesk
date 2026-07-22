import { useRef, useState } from "react"
import { requestIncidentCharge } from "../api/folioApi"
import Modal from "./Modal"

function IncidentChargeRequestModal({ folioId, onRequested, onClose }) {
    const [form, setForm] = useState({ amount: '', reason: '' })
    const initialFormRef = useRef(form)
    const isDirty = JSON.stringify(form) !== JSON.stringify(initialFormRef.current)
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    function handleChange(e) {
        setForm({ ...form, [e.target.name]: e.target.value })
    }

    async function handleSubmit(e) {
        e.preventDefault()
        setError(null)
        setSubmitting(true)
        try {
            await requestIncidentCharge(folioId, Number(form.amount), form.reason)
            onRequested()
        } catch (err) {
            setError(err.response?.status === 409
                ? "No card on file for this stay, or the folio isn't closed."
                : 'Failed to submit incident charge request.')
        }
        setSubmitting(false)
    }

    return (
        <Modal onClose={onClose} size="md" isDirty={isDirty}>
            <h2 className="text-lg text-black font-semibold mb-4">Charge Incident</h2>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div>
                    <label className="block text-sm text-muted mb-1">Amount</label>
                    <input
                        type="number"
                        name="amount"
                        min="0.01"
                        step="0.01"
                        value={form.amount}
                        onChange={handleChange}
                        className="filter-input"
                        required
                    />
                </div>

                <div>
                    <label className="block text-sm text-muted mb-1">Reason</label>
                    <textarea
                        name="reason"
                        value={form.reason}
                        onChange={handleChange}
                        className="filter-input"
                        rows={3}
                        required
                    />
                </div>

                {error && <p className="text-sm text-error">{error}</p>}

                <div className="flex justify-end gap-3 mt-2">
                    <button type="button" onClick={onClose} className="btn btn-secondary">
                        Cancel
                    </button>
                    <button type="submit" className="btn btn-primary" disabled={!isDirty || submitting}>
                        {submitting ? 'Submitting...' : 'Submit for Approval'}
                    </button>
                </div>
            </form>
        </Modal>
    )
}

export default IncidentChargeRequestModal