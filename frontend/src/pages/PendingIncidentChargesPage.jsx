import { useEffect, useState } from "react"
import { getPendingIncidentCharges, approveIncidentCharge, rejectIncidentCharge } from "../api/incidentChargeApi"

function PendingIncidentChargesPage() {
    const [charges, setCharges] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [busyId, setBusyId] = useState(null)
    const [rejectReasons, setRejectReasons] = useState({})

    useEffect(() => {
        loadCharges()
    }, [])

    async function loadCharges() {
        setLoading(true)
        try {
            const res = await getPendingIncidentCharges()
            setCharges(res.data ?? [])
        } catch {
            setError('Failed to load pending incident charges.')
        }
        setLoading(false)
    }

    async function handleApprove(id) {
        setBusyId(id)
        setError(null)
        try {
            await approveIncidentCharge(id)
            await loadCharges()
        } catch {
            setError('Failed to approve incident charge.')
        }
        setBusyId(null)
    }

    async function handleReject(id) {
        const reason = rejectReasons[id]?.trim()
        if (!reason) {
            setError('A rejection reason is required.')
            return
        }

        setBusyId(id)
        setError(null)
        try {
            await rejectIncidentCharge(id, reason)
            await loadCharges()
        } catch {
            setError('Failed to reject incident charge.')
        }
        setBusyId(null)
    }

    return (
        <div>
            <div className="page-header mb-6">
                <h1 className="section-title">Incident Charges</h1>
            </div>

            {error && <p className="text-sm text-error mb-4">{error}</p>}

            {loading ? (
                <p className="text-sm text-muted">Loading...</p>
            ) : charges.length === 0 ? (
                <p className="text-sm text-muted">No pending incident charges.</p>
            ) : (
                <div className="flex flex-col gap-4">
                    {charges.map(charge => (
                        <div key={charge.id} className="bg-warm-white rounded-lg shadow p-4 border-t-4 border-rust">
                            <div className="flex justify-between items-start mb-2">
                                <div>
                                    <p className="font-semibold text-black">${charge.amount.toFixed(2)}</p>
                                    <p className="text-sm text-muted">{charge.reason}</p>
                                </div>
                                <p className="text-xs text-muted">Folio #{charge.folioId}</p>
                            </div>

                            <div className="flex gap-2 items-end mt-3">
                                <input
                                    type="text"
                                    placeholder="Rejection reason (required to reject)"
                                    value={rejectReasons[charge.id] ?? ''}
                                    onChange={e => setRejectReasons({ ...rejectReasons, [charge.id]: e.target.value })}
                                    className="filter-input flex-1"
                                />
                                <button
                                    onClick={() => handleReject(charge.id)}
                                    className="btn btn-secondary"
                                    disabled={busyId === charge.id}
                                >
                                    Reject
                                </button>
                                <button
                                    onClick={() => handleApprove(charge.id)}
                                    className="btn btn-primary"
                                    disabled={busyId === charge.id}
                                >
                                    {busyId === charge.id ? 'Working...' : 'Approve'}
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    )
}

export default PendingIncidentChargesPage