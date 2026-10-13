import { useEffect, useState } from "react"
import { assignHousekeeping, getHousekeepingStaff } from "../api/housekeepingApi"
import Modal from "./Modal"

function AssignHousekeepingModal({ roomId, currentEmployeeId, onSaved, onClose }) {
    const [staff, setStaff] = useState([])
    const [employeeId, setEmployeeId] = useState(currentEmployeeId ?? '')
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        getHousekeepingStaff()
            .then(res => setStaff(res.data ?? []))
            .catch(() => setStaff([]))
    }, [])

    async function handleSubmit(e) {
        e.preventDefault()
        setError(null)
        setSubmitting(true)

        try {
            await assignHousekeeping(roomId, employeeId || null)
            onSaved()
        } catch (err) {
            setError(err.response?.status === 400 ? 'That employee is not active housekeeping staff.' : 'Failed to assign room.')
        }

        setSubmitting(false)
    }

    return (
        <Modal onClose={onClose} size="sm">
            <h2 className="text-lg text-black font-semibold mb-4">Assign Housekeeping</h2>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div>
                    <label className="block text-sm text-muted mb-1">Staff</label>
                    <select value={employeeId} onChange={e => setEmployeeId(e.target.value)} className="filter-input">
                        <option value="">Unassigned</option>
                        {staff.map(employee => (
                            <option key={employee.id} value={employee.id}>{employee.name}</option>
                        ))}
                    </select>
                </div>

                {error && <p className="text-sm text-error">{error}</p>}

                <div className="flex justify-end gap-3 mt-2">
                    <button type="button" onClick={onClose} className="btn btn-secondary">Cancel</button>
                    <button type="submit" className="btn btn-primary" disabled={submitting}>
                        {submitting ? 'Assigning...' : 'Assign'}
                    </button>
                </div>
            </form>
        </Modal>
    )
}

export default AssignHousekeepingModal
