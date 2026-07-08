import { useRef, useState } from "react"
import { updateEmployeePin } from "../api/employeeApi"

function PinModal({ employee, onSaved, onClose }) {
    const [form, setForm] = useState({
        pin: '',
        grantDoorAccess: employee.doorAccessEnabled ?? false
    })
    const [error, setError] = useState('')
    const [confirmPin, setConfirmPin] = useState('')
    const initialFormRef = useRef(form)
    const isDirty = JSON.stringify(form) !== JSON.stringify(initialFormRef.current)

    function handleChange(e) {
        setForm({ ...form, [e.target.name]: e.target.value })
    }

    async function handleSubmit(e) {
        e.preventDefault()
        try {
            setError('')

            if (form.pin !== confirmPin) {
                setError('PINs do not match')
                return
            }

            await updateEmployeePin(employee.id, { pin: form.pin, grantDoorAccess: form.grantDoorAccess })
            onSaved()
        } catch (err) {
            setError('Failed to update PIN')
            console.error(err)
        }
    }

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-warm-white rounded-lg p-6 w-full max-w-md shadow-lg border-t-4 border-rust">
                <h2 className="text-lg text-charcoal font-semibold mb-4">
                    Change PIN
                </h2>

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <div>
                        <label className="block text-sm text-muted mb-1">PIN</label>
                        <input type="password" maxLength={6} inputMode="numeric" placeholder="PIN" name="pin" value={form.pin} onChange={handleChange} className="filter-input" required />
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Confirm PIN</label>
                        <input type="password" maxLength={6} inputMode="numeric" placeholder="Confirm PIN" onChange={e => setConfirmPin(e.target.value)} className="filter-input" required />
                    </div>

                    <div className="flex items-center gap-2">
                        <input type="checkbox" id="grantDoorAccess" checked={form.grantDoorAccess}
                            onChange={e => setForm({ ...form, grantDoorAccess: e.target.checked })} className="h-4 w-4" />
                        <label htmlFor="grantDoorAccess" className="text-sm text-charcoal">Grant smart lock door access with this PIN</label>
                    </div>

                    {form.grantDoorAccess && (
                        <p className="text-xs text-muted -mt-2">PIN must be 6 digits with no repeated or sequential digits (e.g. 482913).</p>
                    )}

                    {error && <p className="text-sm text-rust">{error}</p>}

                    <div className="flex justify-end gap-3 mt-2">
                        <button type="button" onClick={onClose} className="btn btn-secondary">
                            Cancel
                        </button>
                        <button type="submit" className="btn btn-primary" disabled={!isDirty}>
                            Submit
                        </button>
                    </div>
                </form>
            </div>
        </div>
    )
}

export default PinModal