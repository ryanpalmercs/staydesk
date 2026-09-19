import { useRef, useState } from "react"
import { PatternFormat } from "react-number-format"
import { createGuest, updateGuest } from "../api/guestApi"
import { displayPrice, formatPrice, sanitizePrice } from "../utils/price"
import Modal from "./Modal"

function GuestEditModal({ guest = null, onSaved, onClose }) {
    const isEditing = guest != null
    const [priceFocused, setPriceFocused] = useState(false)
    const [form, setForm] = useState({
        firstName: guest?.firstName ?? '',
        lastName: guest?.lastName ?? '',
        email: guest?.email ?? '',
        phoneNumber: guest?.phoneNumber ?? '',
        smsConsent: guest?.smsConsent ?? false,
        legacyPricing: guest?.legacyPricing ?? false,
        legacyPricingAmount: guest?.legacyPricingAmount ?? '',
        legacyRateType: guest?.legacyRateType ?? 'NIGHTLY',
        regularGuest: guest?.regularGuest ?? false,
        guestType: guest?.guestType ?? 'INDIVIDUAL'
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

        if (form.legacyPricing && !(parseFloat(form.legacyPricingAmount) > 0)) {
            setError('Enter a legacy pricing amount greater than zero.')
            return
        }

        setSubmitting(true)
        try {
            const payload = { ...form, legacyPricingAmount: form.legacyPricing ? form.legacyPricingAmount : null }
            const res = isEditing ? await updateGuest(guest.id, payload) : await createGuest(payload)
            onSaved(res.data)
        } catch (err) {
            if (err.response?.status === 409) {
                setError('A guest with that email already exists.')
            } else if (err.response?.status === 400) {
                setError('Please check the fields — phone must be 10 digits, email must be valid, and last name is required for individuals.')
            } else {
                setError('Failed to save guest.')
            }
        }
        setSubmitting(false)
    }

    return (
        <Modal onClose={onClose} size="md" isDirty={isEditing && isDirty}>
            <h2 className="text-lg text-black font-semibold mb-4">{isEditing ? 'Edit Guest' : 'Add Guest'}</h2>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div className="flex gap-2">
                    <button type="button" onClick={() => setForm({ ...form, guestType: 'INDIVIDUAL' })} className={`filter-btn${form.guestType === 'INDIVIDUAL' ? ' active' : ''}`}>Individual</button>
                    <button type="button" onClick={() => setForm({ ...form, guestType: 'BUSINESS', lastName: '' })} className={`filter-btn${form.guestType === 'BUSINESS' ? ' active' : ''}`}>Business Entity</button>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    {form.guestType === 'BUSINESS' ? (
                        <div className="min-w-0 sm:col-span-2">
                            <label className="block text-sm text-muted mb-1">Business Name</label>
                            <input name="firstName" value={form.firstName} onChange={handleChange} className="filter-input w-full" required />
                        </div>
                    ) : (
                        <>
                            <div className="min-w-0">
                                <label className="block text-sm text-muted mb-1">First Name</label>
                                <input name="firstName" value={form.firstName} onChange={handleChange} className="filter-input w-full" required />
                            </div>
                            <div className="min-w-0">
                                <label className="block text-sm text-muted mb-1">Last Name</label>
                                <input name="lastName" value={form.lastName} onChange={handleChange} className="filter-input w-full" required />
                            </div>
                        </>
                    )}
                </div>

                <div>
                    <label className="block text-sm text-muted mb-1">Email <span className="text-muted">(optional)</span></label>
                    <input type="email" name="email" value={form.email} onChange={handleChange} className="filter-input w-full" />
                </div>

                <div>
                    <label className="block text-sm text-muted mb-1">Phone Number</label>
                    <PatternFormat
                        name="phoneNumber"
                        format="(###) ###-####"
                        mask="_"
                        value={form.phoneNumber}
                        onValueChange={values => setForm({ ...form, phoneNumber: values.value })}
                        className="filter-input w-full"
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

                <label className="flex items-start gap-2 text-sm text-muted">
                    <input
                        type="checkbox"
                        checked={form.legacyPricing}
                        onChange={e => setForm({ ...form, legacyPricing: e.target.checked })}
                        className="mt-1"
                    />
                    <span>Legacy Pricing — grandfather this guest into a fixed rate instead of current pricing.</span>
                </label>

                {form.legacyPricing && (
                    <div className="flex gap-2">
                        <div>
                            <label className="block text-sm text-muted mb-1">Legacy Price</label>
                            <input
                                type="text"
                                name="legacyPricingAmount"
                                value={priceFocused ? form.legacyPricingAmount : displayPrice(form.legacyPricingAmount)}
                                onChange={e => setForm({ ...form, legacyPricingAmount: sanitizePrice(e.target.value) })}
                                onFocus={() => setPriceFocused(true)}
                                onBlur={e => { setPriceFocused(false); setForm({ ...form, legacyPricingAmount: formatPrice(sanitizePrice(e.target.value)) }) }}
                                className="filter-input"
                                required
                            />
                        </div>
                        <div>
                            <label className="block text-sm text-muted mb-1">Rate Type</label>
                            <select name="legacyRateType" value={form.legacyRateType} onChange={handleChange} className="filter-input" required>
                                <option value="NIGHTLY">Nightly</option>
                                <option value="WEEKLY_5">Weekly (5-night)</option>
                                <option value="WEEKLY_7">Weekly (7-night)</option>
                            </select>
                        </div>
                    </div>
                )}
                {form.legacyPricing && form.legacyRateType !== 'NIGHTLY' && (
                    <p className="text-xs text-muted -mt-2">
                        This is treated as a flat total for the {form.legacyRateType === 'WEEKLY_5' ? '5' : '7'}-night period and split evenly across the stay, not charged per night.
                    </p>
                )}

                <label className="flex items-start gap-2 text-sm text-muted">
                    <input
                        type="checkbox"
                        checked={form.regularGuest}
                        onChange={e => setForm({ ...form, regularGuest: e.target.checked })}
                        className="mt-1"
                    />
                    <span>Regular Guest — exempt this guest from seasonal rate overrides; they always pay the standard tiered rate.</span>
                </label>

                {error && <p className="text-sm text-error">{error}</p>}

                <div className="flex justify-end gap-3 mt-2">
                    <button type="button" onClick={onClose} className="btn btn-secondary">
                        Cancel
                    </button>
                    <button type="submit" className="btn btn-primary" disabled={(isEditing && !isDirty) || submitting}>
                        {isEditing ? 'Save' : 'Add Guest'}
                    </button>
                </div>
            </form>
        </Modal>
    )
}

export default GuestEditModal
