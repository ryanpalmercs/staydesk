import { useState, useEffect } from "react"
import { useParams } from "react-router-dom"
import { clearGuestLegalHold, flagGuest, getGuest, setGuestLegalHold, unflagGuest } from "../api/guestApi"
import { formatPhone } from "../utils/phone"
import { useAuth } from "../contexts/AuthContext"
import StatusBadge from "../components/StatusBadge"
import { getReservations } from "../api/reservationApi"
import { getRooms } from "../api/roomApi"
import { getRoomTypes } from "../api/roomTypeApi"

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
    const [reservations, setReservations] = useState([])
    const [rooms, setRooms] = useState([])
    const [roomTypes, setRoomTypes] = useState([])

    useEffect(() => {
        fetchGuest()
        getReservations().then(res => setReservations(res.data ?? []))
        getRooms().then(res => setRooms(res.data ?? []))
        getRoomTypes().then(res => setRoomTypes(res.data ?? []))
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

    async function handleLegalHoldToggle() {
        setError(null)
        try {
            if (guest.legalHold) {
                await clearGuestLegalHold(id)
            } else {
                await setGuestLegalHold(id)
            }
            await fetchGuest()
        } catch {
            setError('Failed to update legal hold.')
        }
    }

    if (loading) {
        return <p className="text-gray-500">Loading...</p>
    }

    if (notFound) {
        return <p className="text-muted">Guest not found.</p>
    }

    const roomMap = Object.fromEntries(rooms.map(r => [r.id, r]))
    const roomTypeMap = Object.fromEntries(roomTypes.map(rt => [rt.id, rt]))
    const guestReservations = reservations
        .filter(r => r.guestId === guest.id)
        .sort((a, b) => b.checkInDate.localeCompare(a.checkInDate))

    return (
        <div>
            <div className="page-header mb-6">
                <h1 className="section-title">{guest.name}</h1>
                {guest.flagged && <StatusBadge status="FLAGGED" />}
                {guest.legalHold && <StatusBadge status="LEGAL_HOLD" />}
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
                <div className="mt-4 flex gap-4">
                    {guest.flagged ? (
                        <button onClick={handleUnflag} className="text-sm font-medium text-brown hover:text-rust">
                            Unflag Guest
                        </button>
                    ) : (
                        <button onClick={() => setShowFlagForm(!showFlagForm)} className="text-sm font-medium text-rust hover:text-rust-light">Flag Guest</button>
                    )}
                    <button onClick={handleLegalHoldToggle} className="text-sm font-medium text-brown hover:text-rust">
                        {guest.legalHold ? 'Clear Legal Hold' : 'Place Legal Hold'}
                    </button>
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

            <div className="mt-8">
                <h3 className="section-title mb-4">Reservation History</h3>
                {guestReservations.length === 0 ? (
                    <p className="text-sm text-muted">No reservations yet.</p>
                ) : (
                    <div className="flex flex-col gap-3">
                        {guestReservations.map(res => {
                            const room = roomMap[res.roomId]
                            const roomType = roomTypeMap[res.roomTypeId]
                            return (
                                <div key={res.id} className="feat-card">
                                    <div className="flex items-start justify-between gap-4 mb-2">
                                        <span className="font-semibold text-charcoal">
                                            {room ? `Room ${room.roomNumber}` : `${roomType?.name.replace('_', ' ') ?? 'Room'} (unassigned)`}
                                        </span>
                                        <div className="flex gap-2">
                                            {res.legalHold && <StatusBadge status="LEGAL_HOLD" />}
                                            <StatusBadge status={res.status} />
                                        </div>
                                    </div>
                                    <div className="text-sm text-muted">
                                        {res.checkInDate} → {res.checkOutDate}
                                    </div>
                                </div>
                            )
                        })}
                    </div>
                )}
            </div>
        </div>
    )
}

export default GuestProfilePage