import { useState, useEffect } from "react"
import { getReservations, deleteReservation, checkIn, checkOut, cancelReservation, checkInTerminal, getUnsettledReservations } from "../api/reservationApi"
import { getRooms } from "../api/roomApi"
import { getRoomTypes } from "../api/roomTypeApi"
import ReservationModal from "../components/ReservationModal"
import { getGuests } from "../api/guestApi"
import StatusBadge from "../components/StatusBadge"
import { getFolioByReservationId } from "../api/folioApi"
import CheckInPaymentModal from "../components/CheckInPaymentModal"
import FolioModal from "../components/FolioModal"
import DoorCodeModal from "../components/DoorCodeModal"
import { useAuth } from "../contexts/AuthContext"
import DeleteReservationModal from "../components/DeleteReservationModal"
import ConfirmDialog from "../components/ConfirmDialog"

function ReservationsPage() {
    const { role } = useAuth()
    const canViewDoorCode = ['ADMIN', 'MANAGER', 'FRONT_DESK'].includes(role)
    const canDeleteReservation = role === 'ADMIN'
    const [reservations, setReservations] = useState([])
    const [rooms, setRooms] = useState([])
    const [roomTypes, setRoomTypes] = useState([])
    const [guests, setGuests] = useState([])
    const [loading, setLoading] = useState(true)
    const [modalOpen, setModalOpen] = useState(false)
    const [selectedReservation, setSelectedReservation] = useState(null)
    const [checkInTarget, setCheckInTarget] = useState(null)
    const [reviewFolioId, setReviewFolioId] = useState(null)
    const [doorCodeTarget, setDoorCodeTarget] = useState(null)
    const [deleteTarget, setDeleteTarget] = useState(null)
    const [filters, setFilters] = useState({ dateFrom: '', dateTo: '', roomId: '', guestName: '', confirmationCode: '', status: '' })
    const [error, setError] = useState(null)
    const [sortKey, setSortKey] = useState('checkInDate')
    const [sortDir, setSortDir] = useState('desc')
    const [cancelTarget, setCancelTarget] = useState(null)
    const [unsettledIds, setUnsettledIds] = useState(new Set())

    function handleSort(key) {
        if (sortKey === key) {
            setSortDir(d => d === 'asc' ? 'desc' : 'asc')
        } else {
            setSortKey(key)
            setSortDir('asc')
        }
    }

    useEffect(() => {
        Promise.all([
            fetchReservations(),
            getRooms().then(res => setRooms(res.data)),
            getRoomTypes().then(res => setRoomTypes(res.data)),
            getGuests().then(res => setGuests(res.data))
        ])
    }, [])

    async function fetchReservations() {
        setLoading(true)
        const [res, unsettledRes] = await Promise.all([getReservations(), getUnsettledReservations()])
        setReservations(res.data)
        setUnsettledIds(new Set(unsettledRes.data.map(r => r.id)))
        setLoading(false)
    }

    function openCreate() {
        setSelectedReservation(null)
        setModalOpen(true)
    }

    function openEdit(reservation) {
        setSelectedReservation(reservation)
        setModalOpen(true)
    }

    async function handleDelete(id) {
        await deleteReservation(id)
        await fetchReservations()
    }

    async function handleCancel(id) {
        try {
            await cancelReservation(id)
            await fetchReservations()
        } catch (err) {
            if (err.response?.status === 409) {
                setError('Guest is already checked in.')
            } else {
                setError('Something went wrong.')
            }
        }
    }

    async function handleSaved(newWalkIn) {
        setModalOpen(false)
        await fetchReservations()
        getGuests().then(res => setGuests(res.data))
        if (newWalkIn != null) {
            openCheckIn(newWalkIn.id)
        }
    }

    function handleFilterChange(e) {
        setFilters({ ...filters, [e.target.name]: e.target.value })
    }

    function openCheckIn(id) {
        setCheckInTarget(id)
    }

    async function handleCheckInConfirmed(roomId, incidentalsPaymentMethodId, roomPaymentMethodId) {
        const res = await checkIn(checkInTarget, roomId, incidentalsPaymentMethodId, roomPaymentMethodId)
        await fetchReservations()
        return res.data.doorAccessStatus
    }

    async function handleTerminalCheckInConfirmed(roomId, posDeviceId) {
        const res = await checkInTerminal(checkInTarget, roomId, posDeviceId)
        await fetchReservations()
        return res.data.doorAccessStatus
    }

    async function handleViewFolio(id) {
        try {
            const folioRes = await getFolioByReservationId(id)
            setReviewFolioId(folioRes.data.id)
        } catch (err) {
            setError('Failed to load folio.')
        }
    }

    async function handleCheckOut(id) {
        try {
            await checkOut(id)
            await fetchReservations()
            const folioRes = await getFolioByReservationId(id)
            setReviewFolioId(folioRes.data.id)
        } catch (err) {
            if (err.response?.status === 409) {
                setError('Guest is already checked out')
            } else {
                setError('Something went wrong')
            }
        }
    }

    const roomMap = Object.fromEntries(rooms.map(r => [r.id, r]))
    const roomTypeMap = Object.fromEntries(roomTypes.map(rt => [rt.id, rt]))

    const guestMap = Object.fromEntries(guests.map(g => [g.id, g]))

    const filtered = reservations.filter(res => {
        if (filters.status && res.status !== filters.status) {
            return false
        }
        if (filters.roomId === 'unassigned') {
            if (res.roomId != null) {
                return false
            }
        } else if (filters.roomId && res.roomId !== Number(filters.roomId)) {
            return false
        }
        if (filters.dateFrom && res.checkOutDate < filters.dateFrom) {
            return false
        }
        if (filters.dateTo && res.checkInDate > filters.dateTo) {
            return false
        }
        if (filters.guestName) {
            const fullName = guestMap[res.guestId]
                ? `${guestMap[res.guestId].firstName} ${guestMap[res.guestId].lastName}`.toLowerCase()
                : ''
            if (!fullName.includes(filters.guestName.toLowerCase())) {
                return false
            }
        }
        if (filters.confirmationCode && !res.confirmationCode?.includes(filters.confirmationCode.trim())) {
            return false
        }
        return true
    })

    const sorted = [...filtered].sort((a, b) => {
        let aVal, bVal
        if (sortKey === 'guestName') {
            aVal = guestMap[a.guestId] ? `${guestMap[a.guestId].lastName} ${guestMap[a.guestId].firstName}` : ''
            bVal = guestMap[b.guestId] ? `${guestMap[b.guestId].lastName} ${guestMap[b.guestId].firstName}` : ''
            return sortDir === 'asc' ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal)
        }
        aVal = a[sortKey] ?? ''
        bVal = b[sortKey] ?? ''
        if (aVal < bVal) return sortDir === 'asc' ? -1 : 1
        if (aVal > bVal) return sortDir === 'asc' ? 1 : -1
        return b.id - a.id
    })

    return (
        <div>
            <div className="page-header mb-6">
                <h1 className="section-title">Reservations</h1>
                <button onClick={openCreate} className="btn btn-primary">New Reservation</button>
            </div>

            <div className="filter-bar mb-6">
                <div>
                    <label className="text-muted block text-xs mb-1">From</label>
                    <input type="date" name="dateFrom" value={filters.dateFrom} onChange={handleFilterChange} className="filter-input" />
                </div>
                <div>
                    <label className="text-muted block text-xs mb-1">To</label>
                    <input type="date" name="dateTo" value={filters.dateTo} onChange={handleFilterChange} className="filter-input" />
                </div>
                <div>
                    <label className="text-muted block text-xs mb-1">Room</label>
                    <select name="roomId" value={filters.roomId} onChange={handleFilterChange} className="filter-input">
                        <option value="">All rooms</option>
                        <option value="unassigned">Unassigned</option>
                        {rooms.map(room => (
                            <option key={room.id} value={room.id}>Room {room.roomNumber}</option>
                        ))}
                    </select>
                </div>
                <div>
                    <label className="text-muted block text-xs mb-1">Status</label>
                    <select name="status" value={filters.status} onChange={handleFilterChange} className="filter-input">
                        <option value="">All statuses</option>
                        <option value="CONFIRMED">Confirmed</option>
                        <option value="CHECKED_IN">Checked In</option>
                        <option value="CHECKED_OUT">Checked Out</option>
                        <option value="CANCELLED">Cancelled</option>
                        <option value="NO_SHOW">No Show</option>
                    </select>
                </div>
                <div>
                    <label className="text-muted block text-xs mb-1">Guest</label>
                    <input name="guestName" value={filters.guestName} onChange={handleFilterChange} className="filter-input" />
                </div>
                <div>
                    <label className="text-muted block text-xs mb-1">Confirmation #</label>
                    <input name="confirmationCode" value={filters.confirmationCode} onChange={handleFilterChange} className="filter-input" />
                </div>
            </div>

            <div className="flex gap-2 mb-6 flex-wrap">
                {[
                    { key: 'guestName', label: 'Guest' },
                    { key: 'checkInDate', label: 'Check-in' },
                    { key: 'checkOutDate', label: 'Check-out' },
                    { key: 'status', label: 'Status' },
                ].map(({ key, label }) => (
                    <button
                        key={key}
                        onClick={() => handleSort(key)}
                        className={`filter-btn${sortKey === key ? ' active' : ''}`}
                    >
                        {label}{sortKey === key ? (sortDir === 'asc' ? ' ↑' : ' ↓') : ''}
                    </button>
                ))}
            </div>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : (
                <div className="flex flex-col gap-3">
                    {sorted.map(res => {
                        const guest = guestMap[res.guestId]
                        const room = roomMap[res.roomId]
                        const roomType = roomTypeMap[res.roomTypeId]
                        return (
                            <div key={res.id} className="feat-card relative">
                                {unsettledIds.has(res.id) && (
                                    <span className="absolute top-3 left-1/2 -translate-x-1/2 inline-block px-2 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-800 border border-amber-300">
                                        Payment needed
                                    </span>
                                )}
                                <div className="flex items-start justify-between gap-4 mb-2">
                                    <span className="font-semibold text-black">
                                        {guest ? `${guest.firstName} ${guest.lastName}` : res.guestId}
                                    </span>
                                    <StatusBadge status={res.status} />
                                </div>
                                <div className="flex gap-4 text-sm text-muted mb-3">
                                    <span>{room ? `Room ${room.roomNumber}` : `${roomType?.name.replace('_', ' ') ?? 'Room'} (unassigned)`}</span>
                                    <span>{res.checkInDate} → {res.checkOutDate}</span>
                                    {res.confirmationCode && <span>Conf# {res.confirmationCode}</span>}
                                </div>
                                <div className="flex gap-4 justify-end">
                                    {unsettledIds.has(res.id) && (
                                        <button onClick={() => openCheckIn(res.id)} className="btn btn-secondary text-sm">
                                            Settle Payment
                                        </button>
                                    )}
                                    {res.status === 'CONFIRMED' && (
                                        <button onClick={() => openCheckIn(res.id)} className="text-sm font-medium text-green hover:text-black">Check In</button>
                                    )}
                                    {res.status === 'CHECKED_IN' && (
                                        <button onClick={() => handleCheckOut(res.id)} className="text-sm font-medium text-green hover:text-black">Check Out</button>
                                    )}
                                    {res.status === 'CHECKED_IN' && canViewDoorCode && (
                                        <button onClick={() => setDoorCodeTarget(res)} className="text-sm font-medium text-muted hover:text-green">Door Code</button>
                                    )}
                                    <button onClick={() => openEdit(res)} className="text-sm font-medium text-muted hover:text-green">Edit</button>
                                    {res.status === 'CONFIRMED' && (
                                        <button onClick={() => setCancelTarget(res.id)} className="text-sm font-medium text-muted hover:text-green">Cancel</button>)}
                                    {res.status === 'CHECKED_OUT' && (
                                        <button onClick={() => handleViewFolio(res.id)} className="text-sm font-medium text-muted hover:text-green">View Folio</button>
                                    )}
                                    {canDeleteReservation && (res.status === 'CANCELLED' || res.status === 'CHECKED_OUT' || res.status === 'NO_SHOW') && (
                                        <button onClick={() => setDeleteTarget(res)} className="text-sm font-medium text-muted hover:text-green">Delete</button>
                                    )}
                                </div>
                            </div>
                        )
                    })}
                </div>
            )}

            {modalOpen && (
                <ReservationModal reservation={selectedReservation} onSaved={handleSaved} onClose={() => setModalOpen(false)} />
            )}

            {reviewFolioId != null && (
                <FolioModal folioId={reviewFolioId} onClose={() => setReviewFolioId(null)} onPaid={fetchReservations} />
            )}

            {doorCodeTarget != null && (
                <DoorCodeModal
                    reservationId={doorCodeTarget.id}
                    roomNumber={roomMap[doorCodeTarget.roomId]?.roomNumber ?? '—'}
                    onClose={() => setDoorCodeTarget(null)}
                />
            )}

            {deleteTarget != null && (
                <DeleteReservationModal
                    guest={guestMap[deleteTarget.guestId]}
                    onConfirm={() => handleDelete(deleteTarget.id)}
                    onClose={() => setDeleteTarget(null)}
                />
            )}

            {checkInTarget != null && reservations.find(r => r.id === checkInTarget) && (
                <CheckInPaymentModal
                    reservationId={checkInTarget}
                    reservation={reservations.find(r => r.id === checkInTarget)}
                    onConfirm={handleCheckInConfirmed}
                    onConfirmTerminal={handleTerminalCheckInConfirmed}
                    onClose={() => setCheckInTarget(null)}
                    onCancelReservation={() => handleCancel(checkInTarget)}
                />
            )}

            {cancelTarget != null && (
                <ConfirmDialog
                    message="Cancel this reservation? It will be marked as cancelled."
                    cancelLabel="Nevermind"
                    confirmLabel="Yes, Cancel"
                    onCancel={() => setCancelTarget(null)}
                    onConfirm={async () => {
                        setCancelTarget(null)
                        await handleCancel(cancelTarget)
                    }}
                />
            )}

            {error && <p className="text-sm text-red-600">{error}</p>}
        </div >
    )
}

export default ReservationsPage