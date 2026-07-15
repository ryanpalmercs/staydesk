import { useEffect, useRef, useState } from "react"
import { getEmployeeTypes, updateEmployeeRole } from "../api/employeeApi"

function ChangeRoleModal({ employee, onSaved, onClose }) {
    const [form, setForm] = useState({
        employeeTypeId: employee?.employeeTypeId ?? '',
    })
    const [error, setError] = useState('')
    const [employeeTypes, setEmployeeTypes] = useState([])
    const initialFormRef = useRef(form)
    const isDirty = JSON.stringify(form) !== JSON.stringify(initialFormRef.current)

    function handleChange(e) {
        setForm({ ...form, [e.target.name]: e.target.value })
    }

    useEffect(() => {
        getEmployeeTypes()
            .then(res => setEmployeeTypes(res.data))
            .catch(err => console.error(err))
    }, [])

    async function handleSubmit(e) {
        e.preventDefault()
        try {
            setError('')

            await updateEmployeeRole(employee.id, { employeeTypeId: form.employeeTypeId })
            onSaved()
        } catch (err) {
            console.error(err)
        }
    }

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-warm-white rounded-lg p-6 w-full max-w-md shadow-lg border-t-4 border-rust">
                <h2 className="text-lg text-black font-semibold mb-4">
                    Change Role
                </h2>

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <div>
                        <label className="block text-sm text-muted mb-1">Role</label>
                        <select name="employeeTypeId" value={form.employeeTypeId} onChange={handleChange} className="filter-input" required>
                            <option value="">Select a role</option>
                            {employeeTypes.map(type => (
                                <option key={type.id} value={type.id}>{type.name}</option>
                            ))}
                        </select>
                    </div>

                    <p className="text-sm text-black/60">Role changes take effect on the employee's next login.</p>

                    {error && <p className="text-sm text-error">{error}</p>}

                    <div className="flex justify-end gap-3 mt-2">
                        <button type="button" onClick={onClose} className="btn btn-secondary">
                            Cancel
                        </button>
                        <button type="submit" className="btn btn-primary" disabled={isDirty}>
                            Submit
                        </button>
                    </div>
                </form>
            </div>
        </div>
    )
}

export default ChangeRoleModal