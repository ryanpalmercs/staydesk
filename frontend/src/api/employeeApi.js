import api from './baseApi.js'

export function getEmployees() {
    return api.get('/admin/employees')
}

export function getEmployee(id) {
    return api.get(`/admin/employees/${id}`)
}

export function createEmployee(employee) {
    return api.post('/admin/employees', employee)
}

export function updateEmployeeRole(id, updateEmployeeRequest) {
    return api.put(`/admin/employees/${id}/role`, updateEmployeeRequest)
}

export function updateEmployeePin(id, updateEmployeeRequest) {
    return api.put(`/admin/employees/${id}/pin`, updateEmployeeRequest)
}

export function deleteEmployee(id) {
    return api.delete(`/admin/employees/${id}`)
}

export function getEmployeeTypes() {
    return api.get('/admin/employeeTypes')
}

export function updateEmployeePersonalInfo(id, employee) {
    return api.put(`/admin/employees/${id}`, employee)
}

export function getPayRateTypes() {
    return api.get('/admin/payRateTypes')
}