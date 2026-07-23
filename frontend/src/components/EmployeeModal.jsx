import { useEffect, useRef, useState } from "react"
import { createEmployee, getEmployeeTypes, getPayRateTypes, updateEmployeePersonalInfo } from "../api/employeeApi"
import { displayPrice, formatPrice, sanitizePrice } from "../utils/price"
import { formatPhone } from "../utils/phone"
import Modal from "./Modal"

function EmployeeModal({ employee, onSaved, onClose }) {
    const isEditing = employee != null
    const [focused, setFocused] = useState(false)
    const [phoneFocused, setPhoneFocused] = useState(false)

    const [form, setForm] = useState({
        firstName: employee?.firstName ?? '',
        lastName: employee?.lastName ?? '',
        email: employee?.email ?? '',
        username: employee?.username ?? '',
        employeeTypeId: employee?.employeeTypeId ?? '',
        payRate: employee?.payRate ?? '',
        payRateType: employee?.payRateType ?? 'HOURLY',
        hireDate: employee?.hireDate ?? '',
        pin: '',
        grantDoorAccess: false,
        phone: employee?.contactInfo?.phone ?? '',
        addressLine1: employee?.contactInfo?.addressLine1 ?? '',
        addressLine2: employee?.contactInfo?.addressLine2 ?? '',
        city: employee?.contactInfo?.city ?? '',
        state: employee?.contactInfo?.state ?? '',
        zipCode: employee?.contactInfo?.zipCode ?? ''
    })
    const [error, setError] = useState('')
    const [confirmPin, setConfirmPin] = useState('')
    const [employeeTypes, setEmployeeTypes] = useState([])
    const [payRateTypes, setPayRateTypes] = useState([])
    const initialFormRef = useRef(form)
    const isDirty = JSON.stringify(form) !== JSON.stringify(initialFormRef.current)

    function handleChange(e) {
        setForm({ ...form, [e.target.name]: e.target.value })
    }

    useEffect(() => {
        getEmployeeTypes()
            .then(res => setEmployeeTypes(res.data))
            .catch(err => console.error(err))

        getPayRateTypes()
            .then(res => setPayRateTypes(res.data))
            .catch(err => console.error(err))
    }, [])

    async function handleSubmit(e) {
        e.preventDefault()
        try {
            setError('')

            if (isEditing) {
                await updateEmployeePersonalInfo(employee.id, {
                    ...employee, ...form, contactInfo: {
                        phone: form.phone,
                        addressLine1: form.addressLine1,
                        addressLine2: form.addressLine2,
                        city: form.city,
                        state: form.state,
                        zipCode: form.zipCode
                    }
                })
            } else {
                if (form.pin !== confirmPin) {
                    setError('PINs do not match')
                    return
                }

                await createEmployee(form)
            }

            onSaved()
        } catch (err) {
            setError(isEditing ? 'Failed to update employee' : 'Failed to create employee')
            console.error(err)
        }
    }

    return (
        <Modal onClose={onClose} size="wide" isDirty={isDirty}>
            <h2 className="text-lg text-black font-semibold mb-4">
                {isEditing ? 'Edit Employee' : 'Add Employee'}
            </h2>

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-4">
                        <div className="flex flex-col gap-2">
                            <label className="block text-sm text-muted mb-1">Name</label>
                            <input name="firstName" value={form.firstName} onChange={handleChange} className="filter-input" placeholder="First Name" required />
                            <input name="lastName" value={form.lastName} onChange={handleChange} className="filter-input" placeholder="Last Name" required />
                        </div>

                        <div className="flex flex-col gap-2 min-w-0">
                            <label className="block text-sm text-muted mb-1">Address</label>
                            <input name="addressLine1" value={form.addressLine1} onChange={handleChange} className="filter-input" placeholder="Address Line 1" required />
                            <input name="addressLine2" value={form.addressLine2} onChange={handleChange} className="filter-input" placeholder="Address Line 2" />
                            <div className="flex gap-2">
                                <input name="city" placeholder="City" value={form.city} onChange={handleChange} className="filter-input flex-1 min-w-0" />
                                <input name="state" placeholder="State" value={form.state} onChange={handleChange} className="filter-input w-16" />
                                <input name="zipCode" placeholder="ZIP" value={form.zipCode} onChange={e => setForm({ ...form, zipCode: e.target.value.replace(/\D/g, '').slice(0, 5) })} className="filter-input w-24" />
                            </div>
                        </div>

                        <div>
                            <label className="block text-sm text-muted mb-1">Phone Number</label>
                            <input
                                name="phone"
                                value={phoneFocused ? form.phone : formatPhone(form.phone)}
                                onChange={e => setForm({ ...form, phone: e.target.value.replace(/\D/g, '').slice(0, 10) })}
                                onFocus={() => setPhoneFocused(true)}
                                onBlur={() => { setPhoneFocused(false); setForm({ ...form, phone: form.phone.replace(/\D/g, '') }) }}
                                className="filter-input"
                                placeholder="(###) ###-####"

                            />
                        </div>

                        {isEditing ?
                            <>
                                <div>
                                    <label className="block text-sm text-muted mb-1">Email</label>
                                    <label className="block text-sm text-black mb-1">{employee.email}</label>
                                </div>
                                <div>
                                    <label className="block text-sm text-muted mb-1">User Name</label>
                                    <label className="block text-sm text-black mb-1">{employee.username}</label>
                                </div>
                                <div>
                                    <label className="block text-sm text-muted mb-1">Role</label>
                                    <label className="block text-sm text-black mb-1">
                                        {employeeTypes.find(t => t.id === employee.employeeTypeId)?.name}
                                    </label>
                                </div>
                            </> :
                            <>
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
                            </>
                        }

                        <div>
                            <label className="block text-sm text-muted mb-1">Pay Rate</label>
                            <div className="flex gap-2">
                                <input type="text" name="payRate" value={focused ? form.payRate : displayPrice(form.payRate)} onChange={e => setForm({ ...form, payRate: sanitizePrice(e.target.value) })} onBlur={e => { setFocused(false); setForm({ ...form, payRate: formatPrice(sanitizePrice(e.target.value)) }) }} onFocus={() => setFocused(true)} className="filter-input" required />
                                <select name="payRateType" value={form.payRateType} onChange={handleChange} className="filter-input" required>
                                    {payRateTypes.map(t => (
                                        <option key={t.value} value={t.value}>{t.displayName}</option>
                                    ))}
                                </select>
                            </div>
                        </div>

                        <div>
                            <label className="block text-sm text-muted mb-1">Hire Date</label>
                            <input type="date" name="hireDate" value={form.hireDate} onChange={handleChange} className="filter-input" required />
                        </div>

                        {!isEditing &&
                            <>
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
                                    <label htmlFor="grantDoorAccess" className="text-sm text-black">Grant smart lock door access with this PIN</label>
                                </div>

                                {form.grantDoorAccess && (
                                    <p className="text-xs text-muted -mt-2">PIN must be 6 digits with no repeated or sequential digits (e.g. 482913).</p>
                                )}
                            </>
                        }

                        {error && <p className="text-sm text-error">{error}</p>}

                        <div className="flex justify-end gap-3 mt-2">
                            <button type="button" onClick={onClose} className="btn btn-secondary">
                                Cancel
                            </button>
                            <button type="submit" className="btn btn-primary" disabled={isEditing && !isDirty}>
                                {isEditing ? 'Update Employee' : 'Add Employee'}
                            </button>
                        </div>
                    </div>
                </form>
        </Modal>
    )
}

export default EmployeeModal