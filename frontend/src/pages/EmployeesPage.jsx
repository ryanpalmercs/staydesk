import { useState, useEffect } from "react";
import { getEmployees, getEmployee, deleteEmployee } from "../api/employeeApi";
import StatusBadge from "../components/StatusBadge";
import EmployeeModal from "../components/EmployeeModal";
import ChangeRoleModal from "../components/ChangeRoleModal";
import PinModal from "../components/PinModal";

function EmployeesPage() {
    const [employees, setEmployees] = useState([])
    const [loading, setLoading] = useState(true)
    const [modal, setModal] = useState(null)
    const [selectedEmployee, setSelectedEmployee] = useState(null)
    const [statusFilter, setStatusFilter] = useState('ALL')
    const displayed = employees.filter(r => statusFilter === 'ALL' || r.status === statusFilter)
        .sort((a, b) => a.roomNumber - b.roomNumber)

    useEffect(() => {
        fetchEmployees()
    }, [])

    async function fetchEmployees() {
        setLoading(true)
        const response = await getEmployees()
        setEmployees(response.data)
        setLoading(false)
    }

    function openCreate() {
        setSelectedEmployee(null)
        setModal('create')
    }

    function openRole(employee) {
        setSelectedEmployee(employee)
        setModal('role')
    }

    function openPin(employee) {
        setSelectedEmployee(employee)
        setModal('pin')
    }

    async function handleDelete(id) {
        await deleteEmployee(id)
        fetchEmployees()
    }

    function handleSaved() {
        setModal(null)
        setSelectedEmployee(null)
        fetchEmployees()
    }

    return (
        <div>
            <div className="page-header mb-6">
                <h1 className="section-title">Employees</h1>
                <button onClick={openCreate} className="btn btn-primary">Add Employee</button>
            </div>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : (
                <>
                    <div className="flex gap-2 mb-6 flex-wrap">
                        {['ALL', 'ACTIVE', 'INACTIVE'].map(s => (
                            <button
                                key={s}
                                onClick={() => setStatusFilter(prev => prev === s && s !== 'ALL' ? 'ALL' : s)}
                                className={`filter-btn${statusFilter === s ? ' active' : ''}`}
                            >
                                {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
                            </button>
                        ))}
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        {displayed.map(employee => (
                            <div key={employee.id} className="feat-card flex flex-col gap-3">
                                <div className="flex items-center justify-between">
                                    <span className="font-semibold text-charcoal">{employee.name}</span>
                                    <StatusBadge status={employee.active ? 'ACTIVE' : 'INACTIVE'} />
                                </div>
                                <div className="flex gap-3 justify-end">
                                    <button onClick={() => openRole(employee)} className="text-brown hover:text-rust text-sm font-medium">Change Role</button>
                                    <button onClick={() => openPin(employee)} className="text-brown hover:text-rust text-sm font-medium">Reset Pin</button>
                                    <button onClick={() => handleDelete(employee.id)} className="text-muted hover:text-rust text-sm font-medium">Deactivate</button>
                                </div>
                            </div>
                        ))}
                    </div>
                </>
            )}

            {modal === 'create' && <EmployeeModal onSaved={handleSaved} onClose={() => setModal(null)} />}
            {modal === 'role' && <ChangeRoleModal employee={selectedEmployee} onSaved={handleSaved} onClose={() => setModal(null)} />}
            {modal === 'pin' && <PinModal employee={selectedEmployee} onSaved={handleSaved} onClose={() => setModal(null)} />}
        </div>
    )
}

export default EmployeesPage