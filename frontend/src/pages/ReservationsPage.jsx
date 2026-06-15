import { useState, useEffect } from "react"
import { getReservations, deleteReservation, checkIn, checkOut } from "../api/reservationApi"
import { getRooms } from "../api/roomApi"
import ReservationModal from "../components/ReservationModal"
import { getGuests } from "../api/guestApi"

function ReservationsPage() {
    const [reservations, setReservations] = useState([])
    const [rooms, setRooms] = useState([])
    const [guests, setGuests] = useState([])
    const [loading, setLoading] = useState(true)
    const [modalOpen, setModalOpen] = useState(false)
    const [selectedReservation, setSelectedReservation] = useState(null)
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

    async function handleSaved() {
        setModalOpen(false)
        await fetchReservations()
        getGuests().then(res => setGuests(res.data))
    }

    function handleFilterChange(e) {
        setFilters({ ...filters, [e.target.name]: e.target.value })
    }

    async function handleCheckIn(id) {
        try {
            await checkIn(id)
            await fetchReservations()
        } catch (err) {
            if (err.response?.status === 409) {
                setError('Guest is already checked in.')
            } else {
                setError('Something went wrong.')
            }
        }

    }

    async function handleCheckOut(id) {
        try {
            await checkOut(id)
            await fetchReservations()
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
        <div className="p-8">
            <div className="flex items-center justify-between mb-6">
                <h1 className="text-2xl font-semibold">Reservations</h1>
                <button onClick={openCreate} className="bg-black text-white px-4 py-2 rounded hover:bg-gray-800">
                    New Reservation
                </button>
            </div>

            <div className="flex gap-4 mb-6">
                <div>
                    <label className="block text-xs text-gray-500 mb-1">From</label>
                    <input type="date" name="dateFrom" value={filters.dateFrom} onChange={handleFilterChange} className="border rounded px-3 py-2 text-sm" />
                </div>
                <div>
                    <label className="block text-xs text-gray-500 mb-1">To</label>
                    <input type="date" name="dateTo" value={filters.dateTo} onChange={handleFilterChange} className="border rounded px-3 py-2 text-sm" />
                </div>
                <div>
                    <label className="block text-xs text-gray-500 mb-1">Room</label>
                    <select name="roomId" value={filters.roomId} onChange={handleFilterChange} className="border rounded px-3 py-2 text-sm">
                        <option value="">All rooms</option>
                        {rooms.map(room => (
                            <option key={room.id} value={room.id}>Room {room.roomNumber}</option>
                        ))}
                    </select>
                </div>
                <div>
                    <label className="block text-xs text-gray-500 mb-1">Guest</label>
                    <input name="guestName" value={filters.guestName} onChange={handleFilterChange} className="border rounded px-3 py-2 text-sm" />
                </div>
            </div>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : (
                <table className="w-full text-left border-collapse">
                    <thead>
                        <tr className="border-b text-sm text-gray-500">
                            <th className="pb-2">Guest</th>
                            <th className="pb-2">Room</th>
                            <th className="pb-2">Check-in</th>
                            <th className="pb-2">Check-out</th>
                            <th className="pb-2">Status</th>
                            <th className="pb-2" />
                        </tr>
                    </thead>
                    <tbody>
                        {filtered.map(res => (
                            <tr key={res.id} className="border-b hover:bg-gray-50">
                                <td className="py-3">
                                    {guestMap[res.guestId] ? `${guestMap[res.guestId].firstName} ${guestMap[res.guestId].lastName}` : res.guestId}
                                </td>
                                <td className="py-3">
                                    {roomMap[res.roomId] ? `Room ${roomMap[res.roomId].roomNumber}` : res.roomId}
                                </td>
                                <td className="py-3">{res.checkInDate}</td>
                                <td className="py-3">{res.checkOutDate}</td>
                                <td className="py-3">{res.status}</td>
                                <td className="py-3 flex gap-3 justify-end">
                                    {res.status === 'CONFIRMED' && (
                                        <button onClick={() => handleCheckIn(res.id)} className="text-sm text-green-600 hover:underline">Check In</button>
                                    )}
                                    {res.status === 'CHECKED_IN' && (
                                        <button onClick={() => handleCheckOut(res.id)} className="text-sm text-green-600 hover:underline">Check Out</button>
                                    )}
                                    <button onClick={() => openEdit(res)} className="text-sm text-blue-600 hover:underline">Edit</button>
                                    <button onClick={() => handleDelete(res.id)} className="text-sm text-red-600 hover:underline">Delete</button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            )}

            {modalOpen && (
                <ReservationModal reservation={selectedReservation} onSaved={handleSaved} onClose={() => setModalOpen(false)} />
            )
            }

            {error && <p className="text-sm text-red-600">{error}</p>}
        </div >
    )
}

export default ReservationsPage