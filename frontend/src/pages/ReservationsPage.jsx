import { useState, useEffect } from "react"
import { getReservations, deleteReservation, checkIn, checkOut, cancelReservation } from "../api/reservationApi"
import { getRooms } from "../api/roomApi"
import ReservationModal from "../components/ReservationModal"
import { getGuests } from "../api/guestApi"
import StatusBadge from "../components/StatusBadge"
import { getFolioByReservationId } from "../api/folioApi"
import CheckInPaymentModal from "../components/CheckInPaymentModal"
import FolioModal from "../components/FolioModal"

function ReservationsPage() {
    const [reservations, setReservations] = useState([])
    const [rooms, setRooms] = useState([])
    const [guests, setGuests] = useState([])
    const [loading, setLoading] = useState(true)
    const [modalOpen, setModalOpen] = useState(false)
    const [selectedReservation, setSelectedReservation] = useState(null)
    const [checkInTarget, setCheckInTarget] = useState(null)
    const [reviewFolioId, setReviewFolioId] = useState(null)
    const [filters, setFilters] = useState({ dateFrom: '', dateTo: '', roomId: '', guestName: '' })
    const [error, setError] = useState(null)

    useEffect(() => {
        Promise.all([
            fetchReservations(),
            getRooms().then(res => setRooms(res.data)),
            getGuests().then(res => setGuests(res.data))
        ])
    }, [])

    async function fetchReservations() {
        setLoading(true)
        const res = await getReservations()
        setReservations(res.data)
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

    async function handleSaved() {
        setModalOpen(false)
        await fetchReservations()
        getGuests().then(res => setGuests(res.data))
    }

    function handleFilterChange(e) {
        setFilters({ ...filters, [e.target.name]: e.target.value })
    }

    function openCheckIn(id) {
        setCheckInTarget(id)
    }

    async function handleCheckInConfirmed(roomPaymentMethodId, incidentalsPaymentMethodId) {
        await checkIn(checkInTarget, roomPaymentMethodId, incidentalsPaymentMethodId)
        setCheckInTarget(null)
        await fetchReservations()
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

    const guestMap = Object.fromEntries(guests.map(g => [g.id, g]))

    const filtered = reservations.filter(res => {
        if (filters.roomId && res.roomId !== Number(filters.roomId)) {
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
        return true
    })

    return (
        <div>
            <div className="flex items-center justify-between mb-6">
                <h1 className="section-title">Reservations</h1>
                <button onClick={openCreate} className="btn btn-primary">
                    New Reservation
                </button>
            </div>

            <div className="flex gap-4 mb-6">
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
                        {rooms.map(room => (
                            <option key={room.id} value={room.id}>Room {room.roomNumber}</option>
                        ))}
                    </select>
                </div>
                <div>
                    <label className="text-muted block text-xs mb-1">Guest</label>
                    <input name="guestName" value={filters.guestName} onChange={handleFilterChange} className="filter-input" />
                </div>
            </div>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : (
                <div className="feat-card">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="border-b text-sm text-gray-500">
                                <th className="text-xs font-semibold text-muted uppercase tracking-wide px-6 py-3">Guest</th>
                                <th className="text-xs font-semibold text-muted uppercase tracking-wide px-6 py-3">Room</th>
                                <th className="text-xs font-semibold text-muted uppercase tracking-wide px-6 py-3">Check-in</th>
                                <th className="text-xs font-semibold text-muted uppercase tracking-wide px-6 py-3">Check-out</th>
                                <th className="text-xs font-semibold text-muted uppercase tracking-wide px-6 py-3">Status</th>
                                <th className="text-xs font-semibold text-muted uppercase tracking-wide px-6 py-3" />
                            </tr>
                        </thead>
                        <tbody>
                            {filtered.map(res => (
                                <tr key={res.id} className="hover:bg-tan/30 border-b border-tan">
                                    <td className="px-6 py-4">{guestMap[res.guestId] ? `${guestMap[res.guestId].firstName} ${guestMap[res.guestId].lastName}` : res.guestId}</td>
                                    <td className="px-6 py-4">{roomMap[res.roomId] ? `Room ${roomMap[res.roomId].roomNumber}` : res.roomId}</td>
                                    <td className="px-6 py-4">{res.checkInDate}</td>
                                    <td className="px-6 py-4">{res.checkOutDate}</td>
                                    <td className="px-6 py-4"><StatusBadge status={res.status} /></td>
                                    <td className="px-6 py-4 w-56">
                                        <div className="grid grid-cols-[4.5rem_2.5rem_3.5rem] gap-4 whitespace-nowrap">
                                            <div className="text-right">
                                                {res.status === 'CONFIRMED' && (
                                                    <button onClick={() => openCheckIn(res.id)} className="text-sm font-medium text-rust hover:text-rust-light">Check In</button>
                                                )}
                                                {res.status === 'CHECKED_IN' && (
                                                    <button onClick={() => handleCheckOut(res.id)} className="text-sm font-medium text-rust hover:text-rust-light">Check Out</button>
                                                )}
                                            </div>
                                            <div className="text-right">
                                                <button onClick={() => openEdit(res)} className="text-sm font-medium text-brown hover:text-rust">Edit</button>
                                            </div>
                                            <div className="text-right">
                                                {res.status === 'CONFIRMED' && (
                                                    <button onClick={() => handleCancel(res.id)} className="text-sm font-medium text-muted hover:text-rust">Cancel</button>
                                                )}
                                                {(res.status === 'CANCELLED' || res.status === 'CHECKED_OUT') && (
                                                    <button onClick={() => handleDelete(res.id)} className="text-sm font-medium text-muted hover:text-rust">Delete</button>
                                                )}
                                            </div>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )
            }

            {
                modalOpen && (
                    <ReservationModal reservation={selectedReservation} onSaved={handleSaved} onClose={() => setModalOpen(false)} />
                )
            }

            {
                checkInTarget != null && (
                    <CheckInPaymentModal
                        onConfirm={handleCheckInConfirmed}
                        onClose={() => setCheckInTarget(null)}
                    />
                )
            }

            {
                reviewFolioId != null && (
                    <FolioModal
                        folioId={reviewFolioId}
                        onClose={() => setReviewFolioId(null)}
                        onPaid={fetchReservations}
                    />
                )
            }

            {error && <p className="text-sm text-red-600">{error}</p>}
        </div >
    )
}

export default ReservationsPage