import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { getEmployee } from '../api/employeeApi'
import { clockIn, clockOut, getTimesheet, createManualEntry, updateTimeEntry, deleteTimeEntry } from '../api/timeEntryApi'
import { Clock, ChevronLeft, ChevronRight, Plus, Pencil, Trash2 } from 'lucide-react'

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

function formatTime(iso) {
    if (!iso) return '—'
    return new Date(iso).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
}

function formatDate(str) {
    return new Date(str + 'T12:00:00').toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })
}

function formatHours(h) {
    return h != null ? `${parseFloat(h).toFixed(2)} hrs` : '—'
}

function formatWeekRange(start) {
    const end = new Date(start)
    end.setDate(start.getDate() + 6)
    const fmt = d => d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    return `${fmt(start)} – ${fmt(end)}`
}

const EMPTY_FORM = { date: '', clockIn: '', clockOut: '', hours: '', notes: '' }

export default function TimesheetPage() {
    const { id } = useParams()
    const navigate = useNavigate()
    const { user, role } = useAuth()
    const isOwner = user?.id === id
    const isAdmin = role === 'ADMIN'

    const [employee, setEmployee] = useState(null)
    const [entries, setEntries] = useState([])
    const [loading, setLoading] = useState(true)
    const [weekStart, setWeekStart] = useState(() => getWeekStart(new Date()))
    const [modal, setModal] = useState(null)
    const [selectedEntry, setSelectedEntry] = useState(null)
    const [form, setForm] = useState(EMPTY_FORM)
    const [clockError, setClockError] = useState(null)
    const [submitting, setSubmitting] = useState(false)
    const [elapsed, setElapsed] = useState('')

    const weekEnd = new Date(weekStart)
    weekEnd.setDate(weekStart.getDate() + 6)

    const openEntry = entries.find(e => !e.clockOut)
    const isClockedIn = !!openEntry


    useEffect(() => {
        if (!isOwner && !isAdmin) {
            navigate('/', { replace: true })
            return
        }
        if (isAdmin) {
            getEmployee(id).then(res => setEmployee(res.data)).catch(() => { })
        }
    }, [id])

    useEffect(() => {
        fetchEntries()
    }, [id, weekStart])

    useEffect(() => {
        if (!isClockedIn || !openEntry) {
            setElapsed('')
            return
        }

        function tick() {
            const totalSeconds = Math.floor((Date.now() - new Date(openEntry.clockIn).getTime()) / 1000)
            const h = Math.floor(totalSeconds / 3600)
            const m = Math.floor((totalSeconds % 3600) / 60)
            const s = totalSeconds % 60
            setElapsed(
                h > 0
                    ? `${h}h ${String(m).padStart(2, '0')}m ${String(s).padStart(2, '0')}s`
                    : `${m}m ${String(s).padStart(2, '0')}s`
            )
        }

        tick()
        const id = setInterval(tick, 1000)
        return () => clearInterval(id)
    }, [isClockedIn, openEntry?.clockIn])

    async function fetchEntries() {
        setLoading(true)
        try {
            const res = await getTimesheet(id, toISODate(weekStart), toISODate(weekEnd))
            setEntries(res.data)
        } catch (err) {
            console.error(err)
        }
        setLoading(false)
    }

    async function handleClockIn() {
        setClockError(null)
        setSubmitting(true)
        try {
            await clockIn(id, null)
            await fetchEntries()
        } catch (err) {
            setClockError(err.response?.data || 'Clock-in failed')
        }
        setSubmitting(false)
    }

    async function handleClockOut() {
        setClockError(null)
        setSubmitting(true)
        try {
            await clockOut(id, null)
            await fetchEntries()
        } catch (err) {
            setClockError(err.response?.data || 'Clock-out failed')
        }
        setSubmitting(false)
    }

    function openAdd() {
        setForm({ ...EMPTY_FORM, date: toISODate(new Date()) })
        setSelectedEntry(null)
        setModal('entry')
    }

    function openEdit(entry) {
        setSelectedEntry(entry)
        setForm({
            date: entry.date,
            clockIn: entry.clockIn ? entry.clockIn.slice(11, 16) : '',
            clockOut: entry.clockOut ? entry.clockOut.slice(11, 16) : '',
            hours: entry.hours ?? '',
            notes: entry.notes ?? ''
        })
        setModal('entry')
    }

    async function handleDelete(entryId) {
        if (!confirm('Delete this time entry?')) return
        try {
            await deleteTimeEntry(entryId)
            fetchEntries()
        } catch (err) {
            console.error(err)
        }
    }

    async function handleSubmit(e) {
        e.preventDefault()
        setSubmitting(true)
        try {
            const payload = {
                date: form.date,
                clockIn: form.clockIn ? `${form.date}T${form.clockIn}:00` : null,
                clockOut: form.clockOut ? `${form.date}T${form.clockOut}:00` : null,
                hours: form.hours !== '' ? parseFloat(form.hours) : null,
                notes: form.notes || null
            }
            if (selectedEntry) {
                await updateTimeEntry(selectedEntry.id, payload)
            } else {
                await createManualEntry({ ...payload, employeeId: id })
            }
            setModal(null)
            fetchEntries()
        } catch (err) {
            console.error(err)
        }
        setSubmitting(false)
    }

    const totalHours = entries.reduce((sum, e) => sum + (e.hours ? parseFloat(e.hours) : 0), 0)

    return (
        <div>
            <div className="page-header mb-6">
                <div>
                    <button onClick={() => navigate(-1)} className="text-muted hover:text-rust text-sm mb-1 flex items-center gap-1">
                        <ChevronLeft size={14} /> Back
                    </button>
                    <h1 className="section-title">
                        {isAdmin && employee ? `${employee.name} — Timesheet` : 'My Timesheet'}
                    </h1>
                </div>
                {isAdmin && (
                    <button onClick={openAdd} className="btn btn-primary flex items-center gap-2">
                        <Plus size={16} /> Add Entry
                    </button>
                )}
            </div>

            {isOwner && (
                <div className="feat-card mb-6 flex flex-col items-center gap-3 py-6">
                    <div className="flex items-center gap-2 text-muted text-sm">
                        <Clock size={16} />
                        {isClockedIn
                            ? <span>Clocked in since <strong>{formatTime(openEntry.clockIn)}</strong> · {elapsed}</span>
                            : <span>Currently clocked out</span>
                        }
                    </div>
                    {clockError && <p className="text-rust text-sm">{clockError}</p>}
                    {isClockedIn
                        ? <button onClick={handleClockOut} disabled={submitting} className="btn btn-primary px-8 py-3 text-lg">Clock Out</button>
                        : <button onClick={handleClockIn} disabled={submitting} className="btn btn-primary px-8 py-3 text-lg">Clock In</button>
                    }
                </div>
            )}

            <div className="flex items-center justify-between mb-4">
                <button onClick={() => setWeekStart(d => { const p = new Date(d); p.setDate(d.getDate() - 7); return p })} className="text-brown hover:text-rust">
                    <ChevronLeft size={20} />
                </button>
                <span className="font-medium text-charcoal">{formatWeekRange(weekStart)}</span>
                <button onClick={() => setWeekStart(d => { const n = new Date(d); n.setDate(d.getDate() + 7); return n })} className="text-brown hover:text-rust">
                    <ChevronRight size={20} />
                </button>
            </div>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : entries.length === 0 ? (
                <p className="text-muted text-sm">No entries for this week.</p>
            ) : (
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="text-muted border-b">
                                <th className="text-left pb-2 font-medium">Date</th>
                                <th className="text-left pb-2 font-medium">Clock In</th>
                                <th className="text-left pb-2 font-medium">Clock Out</th>
                                <th className="text-left pb-2 font-medium">Hours</th>
                                <th className="text-left pb-2 font-medium">Notes</th>
                                {isAdmin && <th className="pb-2" />}
                            </tr>
                        </thead>
                        <tbody>
                            {entries.map(entry => (
                                <tr key={entry.id} className="border-b last:border-0">
                                    <td className="py-2">{formatDate(entry.date)}</td>
                                    <td className="py-2">{formatTime(entry.clockIn)}</td>
                                    <td className="py-2">{formatTime(entry.clockOut)}</td>
                                    <td className="py-2">{formatHours(entry.hours)}</td>
                                    <td className="py-2 text-muted">{entry.notes ?? '—'}</td>
                                    {isAdmin && (
                                        <td className="py-2">
                                            <div className="flex gap-3 justify-end">
                                                <button onClick={() => openEdit(entry)} className="text-brown hover:text-rust"><Pencil size={14} /></button>
                                                <button onClick={() => handleDelete(entry.id)} className="text-muted hover:text-rust"><Trash2 size={14} /></button>
                                            </div>
                                        </td>
                                    )}
                                </tr>
                            ))}
                        </tbody>
                        <tfoot>
                            <tr className="font-semibold text-charcoal">
                                <td colSpan={3} className="pt-3">Total</td>
                                <td className="pt-3">{totalHours.toFixed(2)} hrs</td>
                                <td colSpan={isAdmin ? 2 : 1} />
                            </tr>
                        </tfoot>
                    </table>
                </div>
            )}

            {modal === 'entry' && (
                <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setModal(null)}>
                    <div className="bg-warm-white rounded-lg p-6 w-full max-w-md shadow-lg border-t-4 border-rust" onClick={e => e.stopPropagation()}>
                        <h2 className="text-lg text-charcoal font-semibold mb-4">{selectedEntry ? 'Edit Time Entry' : 'Add Time Entry'}</h2>
                        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                            <div>
                                <label className="block text-sm text-muted mb-1">Date</label>
                                <input type="date" value={form.date} onChange={e => setForm(f => ({ ...f, date: e.target.value }))} className="filter-input" required />
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm text-muted mb-1">Clock In</label>
                                    <input type="time" value={form.clockIn} onChange={e => setForm(f => ({ ...f, clockIn: e.target.value }))} className="filter-input w-full" />
                                </div>
                                <div>
                                    <label className="block text-sm text-muted mb-1">Clock Out</label>
                                    <input type="time" value={form.clockOut} onChange={e => setForm(f => ({ ...f, clockOut: e.target.value }))} className="filter-input w-full" />
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm text-muted mb-1">Hours</label>
                                <input type="number" step="0.01" min="0" value={form.hours} onChange={e => setForm(f => ({ ...f, hours: e.target.value }))} className="filter-input w-full" placeholder="Leave blank to compute from time entries" />
                            </div>
                            <div>
                                <label className="block text-sm text-muted mb-1">Notes</label>
                                <input type="text" value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} className="filter-input w-full" />
                            </div>
                            <div className="flex justify-end gap-3">
                                <button type="button" onClick={() => setModal(null)} className="btn btn-secondary">Cancel</button>
                                <button type="submit" disabled={submitting} className="btn btn-primary">Save</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    )
}