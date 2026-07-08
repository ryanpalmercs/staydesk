import { useState, useEffect } from "react"
import { useParams } from "react-router-dom"
import { flagGuest, getGuest, unflagGuest } from "../api/guestApi"
import { formatPhone } from "../utils/phone"
import { useAuth } from "../contexts/AuthContext"
import StatusBadge from "../components/StatusBadge"

function GuestProfilePage() {
    const { id } = useParams()
    const { role } = useAuth()
    const canManage = ['ADMIN', 'MANAGER'].includes(role)

    const [guest, setGuest] = useState(null)
    const [loading, setLoading] = useState(true)
    const [notFound, setNotFound] = useState(false)
    const [showFlagForm, setShowFlagForm] = useState(false)
    const [flagReason, setFlagReason] = useState('')
    const [error, setError] = useState(null)

    useEffect(() => {
        fetchGuest()
    }, [id])

    async function fetchGuest() {
        setLoading(true)
        setNotFound(false)
        try {
            const res = await getGuest(id)
            setGuest(res.data)
        } catch (err) {
            if (err.response?.status === 404) {
                setNotFound(true)
            }
        }
        setLoading(false)
    }

    async function handleFlag(e) {
        e.preventDefault()
        setError(null)
        try {
            await flagGuest(id, flagReason)
            setShowFlagForm(false)
            setFlagReason('')
            await fetchGuest()
        } catch {
            setError('Failed to flag guest.')
        }
    }

    async function handleUnflag() {
        setError(null)
        try {
            await unflagGuest(id)
            await fetchGuest()
        } catch {
            setError('Failed to unflag guest.')
        }
    }

    if (loading) {
        return <p className="text-gray-500">Loading...</p>
    }

    if (notFound) {
        return <p className="text-muted">Guest not found.</p>
    }

    return (
        <div>
            <div className="page-header mb-6">
                <h1 className="section-title">{guest.name}</h1>
                {guest.flagged && <StatusBadge status="FLAGGED" />}
            </div>
            <div className="flex gap-2 mb-6 flex-wrap">
                <div>
                    <span className="block text-sm text-muted mb-1">Email</span>
                    <p className="text-sm text-charcoal">{guest.email}</p>
                </div>
                <div>
                    <span className="block text-sm text-muted mb-1">Phone Number</span>
                    <p className="text-sm text-charcoal">{formatPhone(guest.phoneNumber)}</p>
                </div>
            </div>

            {guest.flagged && (
                <div className="mt-4">
                    <span className="block text-sm text-muted mb-1">Flag Reason</span>
                    <p className="text-sm text-rust">{guest.flagReason}</p>
                    <p className="text-xs text-muted mt-1">Flagged {new Date(guest.flaggedDate).toLocaleDateString()}</p>
                </div>
            )}

            {canManage && (
                <div className="mt-4">
                    {guest.flagged ? (
                        <button onClick={handleUnflag} className="text-sm font-medium text-brown hover:text-rust">
                            Unflag Guest
                        </button>
                    ) : (
                        <button onClick={() => setShowFlagForm(!showFlagForm)} className="text-sm font-medium text-rust hover:text-rust-light">Flag Guest</button>
                    )}
                </div>
            )}

            {showFlagForm && (
                <form onSubmit={handleFlag} className="flex flex-col gap-2 mt-2 max-w-md">
                    <span className="block text-sm text-muted mb-1">Reason</span>
                    <textarea value={flagReason} onChange={e => setFlagReason(e.target.value)} className="filter-input" required />
                    <div className="flex justify-end gap-3">
                        <button type="button" onClick={() => setShowFlagForm(false)} className="btn btn-secondary">Cancel</button>
                        <button type="submit" className="btn btn-primary">Flag</button>
                    </div>
                </form>
            )}

            {error && <p className="text-sm text-rust mt-2">{error}</p>}
        </div>
    )
}

export default GuestProfilePage