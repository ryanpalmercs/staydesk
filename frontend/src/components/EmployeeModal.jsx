import { useEffect, useState } from "react"
import { createEmployee, getEmployeeTypes } from "../api/employeeApi"

function EmployeeModal({ employee, onSaved, onClose }) {
    const [form, setForm] = useState({
        firstName: employee?.firstName ?? '',
        lastName: employee?.lastName ?? '',
        email: employee?.email ?? '',
        username: employee?.username ?? '',
        employeeTypeId: employee?.employeeTypeId ?? '',
        payRate: employee?.payRate ?? '',
        hireDate: employee?.hireDate ?? '',
        pin: ''
    })
    const [error, setError] = useState('')
    const [confirmPin, setConfirmPin] = useState('')
    const [employeeTypes, setEmployeeTypes] = useState([])

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

            if (form.pin !== confirmPin) {
                setError('PINs do not match')
                return
            }

            await createEmployee(form)
            onSaved()
        } catch (err) {
            setError('Failed to create employee')
            console.error(err)
        }
    }

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-warm-white rounded-lg p-6 w-full max-w-md shadow-lg border-t-4 border-rust">
                <h2 className="text-lg text-charcoal font-semibold mb-4">
                    Add Employee
                </h2>

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <div>
                        <label className="block text-sm text-muted mb-1">Name</label>
                        <input name="firstName" value={form.firstName} onChange={handleChange} className="filter-input" required />
                        <input name="lastName" value={form.lastName} onChange={handleChange} className="filter-input" required />
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Email</label>
                        <input name="email" value={form.email} onChange={handleChange} className="filter-input" required />
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">User Name</label>
                        <input name="username" value={form.username} onChange={handleChange} className="filter-input" required />
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Role</label>
                        <select name="employeeTypeId" value={form.employeeTypeId} onChange={handleChange} className="filter-input" required>
                            <option value="">Select a role</option>
                            {employeeTypes.map(type => (
                                <option key={type.id} value={type.id}>{type.name}</option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Pay Rate</label>
                        <input type="number" name="payRate" value={form.payRate} onChange={handleChange} className="filter-input" step="0.01" required />
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Hire Date</label>
                        <input type="date" name="hireDate" value={form.hireDate} onChange={handleChange} className="filter-input" required />
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">PIN</label>
                        <input type="password" maxLength={6} inputMode="numeric" placeholder="PIN" name="pin" value={form.pin} onChange={handleChange} className="filter-input" required />
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Confirm PIN</label>
                        <input type="password" maxLength={6} inputMode="numeric" placeholder="Confirm PIN" onChange={e => setConfirmPin(e.target.value)} className="filter-input" required />
                    </div>

                    {error && <p className="text-sm text-rust">{error}</p>}

                    <div className="flex justify-end gap-3 mt-2">
                        <button type="button" onClick={onClose} className="btn btn-secondary">
                            Cancel
                        </button>
                        <button type="submit" className="btn btn-primary">
                            Add Employee
                        </button>
                    </div>
                </form>
            </div >
        </div >
    )
}

export default EmployeeModal