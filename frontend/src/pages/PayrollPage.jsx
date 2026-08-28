import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getEmployees } from '../api/employeeApi'
import { exportTimesheets, getPayPeriodEntries } from '../api/timeEntryApi'
import { displayPrice } from '../utils/price'
import { syncEmployeesToQuickBooks } from '../api/payrollApi'
import { ChevronLeft, ChevronRight, Download, RefreshCw } from 'lucide-react'

function getWeekStart(date) {
    const d = new Date(date)
    d.setHours(0, 0, 0, 0)
    const day = d.getDay()
    d.setDate(d.getDate() + (day === 0 ? -6 : 1 - day))
    return d
}

function toISODate(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function formatWeekRange(start) {
    const end = new Date(start)
    end.setDate(start.getDate() + 6)
    const fmt = d => d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    return `${fmt(start)} – ${fmt(end)}`
}

export default function PayrollPage() {
    const navigate = useNavigate()
    const [employees, setEmployees] = useState([])
    const [entries, setEntries] = useState([])
    const [loading, setLoading] = useState(true)
    const [weekStart, setWeekStart] = useState(() => getWeekStart(new Date()))
    const [exporting, setExporting] = useState(false)
    const [syncingEmployees, setSyncingEmployees] = useState(false)
    const [syncResult, setSyncResult] = useState(null)
    const [error, setError] = useState(null)

    const weekEnd = new Date(weekStart)
    weekEnd.setDate(weekStart.getDate() + 6)

    useEffect(() => {
        getEmployees().then(res => setEmployees(res.data)).catch(err => console.error(err))
    }, [])

    useEffect(() => {
        fetchEntries()
    }, [weekStart])

    async function fetchEntries() {
        setLoading(true)
        try {
            const res = await getPayPeriodEntries(toISODate(weekStart), toISODate(weekEnd))
            setEntries(res.data)
        } catch (err) {
            setError(`Failed to retrieve data for pay period ${toISODate(weekStart)} - ${toISODate(weekEnd)}.`)
            console.error(err)
        }
        setLoading(false)
    }

    async function handleExport(format) {
        setExporting(true)

        try {
            const res = await exportTimesheets(toISODate(weekStart), toISODate(weekEnd), format)
            const url = URL.createObjectURL(res.data)
            const a = document.createElement('a')

            a.href = url
            a.download = `timesheet-${toISODate(weekStart)}-${toISODate(weekEnd)}.${format}`
            a.click()
            URL.revokeObjectURL(url)
        } catch (err) {
            setError('Export failed. Please try again.')
            console.error(err)
        }

        setExporting(false)
    }

    async function handleSyncEmployees() {
        setSyncingEmployees(true)
        setSyncResult(null)
        setError(null)

        try {
            const res = await syncEmployeesToQuickBooks()
            const { created, matched, failed } = res.data
            setSyncResult({ created: created.length, matched: matched.length, failed })
        } catch (err) {
            setError('Failed to sync employees to QuickBooks. Please try again.')
            console.error(err)
        }

        setSyncingEmployees(false)
    }

    const rows = employees
        .filter(e => e.active)
        .map(emp => {
            const empEntries = entries.filter(e => e.employeeId === emp.id)
            const totalHours = empEntries.reduce((sum, e) => sum + (e.hours ? parseFloat(e.hours) : 0), 0)
            const daysWorked = new Set(empEntries.map(e => e.date)).size
            const estPay = totalHours * parseFloat(emp.payRate)
            return { emp, totalHours, daysWorked, estPay }
        })
        .sort((a, b) => a.emp.lastName.localeCompare(b.emp.lastName))

    const grandTotal = rows.reduce((sum, r) => sum + r.estPay, 0)

    return (
        <div>
            <div className="page-header mb-6">
                <h1 className="section-title">Payroll</h1>
                <div className="flex gap-2">
                    <button onClick={handleSyncEmployees} disabled={syncingEmployees} className="btn btn-secondary flex items-center gap-2">
                        <RefreshCw size={16} /> {syncingEmployees ? 'Syncing...' : 'Sync Employees to QuickBooks'}
                    </button>
                    <button onClick={() => handleExport('csv')} disabled={exporting} className="btn btn-secondary flex items-center gap-2">
                        <Download size={16} /> CSV
                    </button>
                    <button onClick={() => handleExport('pdf')} disabled={exporting} className="btn btn-secondary flex items-center gap-2">
                        <Download size={16} /> PDF
                    </button>
                </div>
            </div>

            {syncResult && (
                <p className="text-sm text-muted mb-4">
                    QuickBooks employee sync: {syncResult.created} created, {syncResult.matched} matched
                    {syncResult.failed.length > 0 && `, ${syncResult.failed.length} failed (${syncResult.failed.join('; ')})`}.
                </p>
            )}

            <div className="flex items-center justify-between mb-6">
                <button onClick={() => setWeekStart(d => { const p = new Date(d); p.setDate(d.getDate() - 7); return p })} className="text-muted hover:text-green">
                    <ChevronLeft size={20} />
                </button>
                <span className="font-medium text-black">{formatWeekRange(weekStart)}</span>
                <button onClick={() => setWeekStart(d => { const n = new Date(d); n.setDate(d.getDate() + 7); return n })} className="text-muted hover:text-green">
                    <ChevronRight size={20} />
                </button>
            </div>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : rows.length === 0 ? (
                <p className="text-muted text-sm">No active employees.</p>
            ) : (
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="text-muted border-b">
                                <th className="text-left pb-2 font-medium">Employee</th>
                                <th className="text-left pb-2 font-medium">Days</th>
                                <th className="text-left pb-2 font-medium">Total Hours</th>
                                <th className="text-left pb-2 font-medium">Pay Rate</th>
                                <th className="text-left pb-2 font-medium">Est. Pay</th>
                            </tr>
                        </thead>
                        <tbody>
                            {rows.map(({ emp, totalHours, daysWorked, estPay }) => (
                                <tr
                                    key={emp.id}
                                    onClick={() => navigate(`/timesheet/${emp.id}`)}
                                    className="border-b last:border-0 cursor-pointer hover:bg-gray-50"
                                >
                                    <td className="py-3 font-medium text-black">{emp.name}</td>
                                    <td className="py-3">{daysWorked}</td>
                                    <td className="py-3">{totalHours.toFixed(2)} hrs</td>
                                    <td className="py-3">{displayPrice(emp.payRate)}/{emp.payRateTypeDisplayName}</td>
                                    <td className="py-3">{displayPrice(estPay)}</td>
                                </tr>
                            ))}
                        </tbody>
                        <tfoot>
                            <tr className="font-semibold text-black">
                                <td colSpan={4} className="pt-3">Total Est. Payroll</td>
                                <td className="pt-3">{displayPrice(grandTotal)}</td>
                            </tr>
                        </tfoot>
                    </table>
                </div>
            )}

            {error && <p className="text-error text-sm mt-2">{error}</p>}
        </div>
    )
}