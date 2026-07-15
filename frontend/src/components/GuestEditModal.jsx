import { useRef, useState } from "react"
import { PatternFormat } from "react-number-format"
import { updateGuest } from "../api/guestApi"

function GuestEditModal({ guest, onSaved, onClose }) {
    const [form, setForm] = useState({
        firstName: guest.firstName,
        lastName: guest.lastName,
        email: guest.email,
        phoneNumber: guest.phoneNumber,
        smsConsent: guest.smsConsent
    })
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
            await updateGuest(guest.id, form)
            onSaved()
        } catch (err) {
            if (err.response?.status === 400) {
                setError('Please check the fields — phone must be 10 digits and email must be valid.')
            } else {
                setError('Failed to save guest.')
            }
        }
        setSubmitting(false)
    }

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-warm-white rounded-lg p-6 w-full max-w-md shadow-lg border-t-4 border-rust">
                <h2 className="text-lg text-black font-semibold mb-4">Edit Guest</h2>

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm text-muted mb-1">First Name</label>
                            <input name="firstName" value={form.firstName} onChange={handleChange} className="filter-input" required />
                        </div>
                        <div>
                            <label className="block text-sm text-muted mb-1">Last Name</label>
                            <input name="lastName" value={form.lastName} onChange={handleChange} className="filter-input" required />
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Email</label>
                        <input type="email" name="email" value={form.email} onChange={handleChange} className="filter-input" required />
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Phone Number</label>
                        <PatternFormat
                            name="phoneNumber"
                            format="(###) ###-####"
                            mask="_"
                            value={form.phoneNumber}
                            onValueChange={values => setForm({ ...form, phoneNumber: values.value })}
                            className="filter-input"
                            placeholder="(###) ###-####"
                            required
                        />
                    </div>

                    <label className="flex items-start gap-2 text-sm text-muted">
                        <input
                            type="checkbox"
                            checked={form.smsConsent}
                            onChange={e => setForm({ ...form, smsConsent: e.target.checked })}
                            className="mt-1"
                        />
                        <span>
                            Guest consents to receive SMS text messages (door codes, check-in/checkout confirmations). Message and
                            data rates may apply. See our <a href="/sms-terms" target="_blank" rel="noopener noreferrer" className="text-green underline">SMS Terms</a>.
                        </span>
                    </label>

                    {error && <p className="text-sm text-error">{error}</p>}

                    <div className="flex justify-end gap-3 mt-2">
                        <button type="button" onClick={onClose} className="btn btn-secondary">
                            Cancel
                        </button>
                        <button type="submit" className="btn btn-primary" disabled={!isDirty || submitting}>
                            Save
                        </button>
                    </div>
                </form>
            </div>
        </div>
    )
}

export default GuestEditModal
