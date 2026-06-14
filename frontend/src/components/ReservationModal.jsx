import { useEffect, useState } from "react"
import { createReservation, updateReservation } from "../api/reservationApi"
import { getRooms } from "../api/roomApi"
import { createGuest, getGuests } from "../api/guestApi"

function ReservationModal({ reservation, onSaved, onClose }) {
    const isEditing = reservation != null

    const [rooms, setRooms] = useState([])
    const [guests, setGuests] = useState([])
    const [guestMode, setGuestMode] = useState('search')
    let [form, setForm] = useState({
        guestId: reservation?.guestId ?? '',
        roomId: reservation?.roomId ?? '',
        checkInDate: reservation?.checkInDate ?? '',
        checkOutDate: reservation?.checkOutDate ?? '',
        status: reservation?.status ?? 'CONFIRMED'
    })

    const [guestForm, setGuestForm] = useState({
        firstName: '',
        lastName: '',
        email: '',
        phoneNumber: ''
    })

    const [error, setError] = useState(null)

    useEffect(() => {
        getRooms().then(res => setRooms(res.data ?? [])),
            getGuests().then(res => setGuests(res.data ?? []))
    }, [])

    function handleChange(e) {
        setForm({ ...form, [e.target.name]: e.target.value })
    }

    function handleGuestFieldChange(e) {
        setGuestForm({ ...guestForm, [e.target.name]: e.target.value })
    }

    function onGuestModeChange() {
        setGuestMode(guestMode === 'search' ? 'create' : 'search')
    }

    async function handleSubmit(e) {
        console.log(guestMode)
        e.preventDefault()

        setError(null)

        if (form.checkOutDate <= form.checkInDate) {
            setError('Check-out date must be after check-in date.')
            return
        }

        let submittedForm = { ...form }

        if (guestMode === 'create') {
            console.log('Creating guest')

            try {
                const res = await createGuest(guestForm)
                submittedForm = { ...form, guestId: res.data.id }
                setGuestMode('search')
                const guestsRes = await getGuests()
                setGuests(guestsRes.data)
                setForm({ ...form, guestId: res.data.id })
            } catch (err) {
                if (err.response?.status === 400) {
                    setError('Phone number must be 10 digits.')
                } else if (err.response?.status === 409) {
                    setError('A guest with that email already exists.')
                } else {
                    console.log(err)
                    setError('Failed to create guest.')
                }

                return
            }
        }

        console.log(submittedForm)

        try {
            if (isEditing) {
                await updateReservation(reservation.id, { ...reservation, ...submittedForm })
            } else {
                await createReservation(submittedForm)
            }

            onSaved()
        } catch (err) {
            if (err.response?.status === 400) {
                setError('Room is unavailable or dates conflict with an existing reservation.')
            } else if (err.response?.status === 404) {
                setError('Room not found.')
            } else {
                setError('Something went wrong.')
            }
        }
    }

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-lg p-6 w-full max-w-md shadow-lg">
                <h2 className="text-lg font-semibold mb-4">
                    {isEditing ? 'Edit Reservation' : 'New Reservation'}
                </h2>

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <div>
                        <label className="block text-sm text-gray-600 mb-1">Guest</label>
                        {guestMode === 'search' ? (
                            <select name="guestId" value={form.guestId} onChange={handleChange} className="w-full border rounded px-3 py-2 text-sm" required>
                                <option value="">Select a guest</option>
                                {guests.map(guest => (
                                    <option key={guest.id} value={guest.id}>
                                        {guest.firstName} {guest.lastName}
                                    </option>
                                ))}
                            </select>
                        ) : (
                            <div className="flex flex-col gap-2">
                                <input name="firstName" placeholder="First name" onChange={handleGuestFieldChange} className="w-full border rounded px-3 py-2 text-sm" required />
                                <input name="lastName" placeholder="Last name" onChange={handleGuestFieldChange} className="w-full border rounded px-3 py-2 text-sm" required />
                                <input name="email" placeholder="Email" onChange={handleGuestFieldChange} className="w-full border rounded px-3 py-2 text-sm" required />
                                <input name="phoneNumber" placeholder="Phone (10 digits)" onChange={handleGuestFieldChange} className="w-full border rounded px-3 py-2 text-sm" required />
                            </div>
                        )}
                        <button type="button" onClick={onGuestModeChange} className="px-4 py-2 text-sm text-blue-600 hover:underline">
                            {guestMode === 'search' ? 'New Guest' : 'Select Existing'}
                        </button>
                    </div>

                    <div>
                        <label className="block text-sm text-gray-600 mb-1">Room</label>
                        <select name="roomId" value={form.roomId} onChange={handleChange} className="w-full border rounded px-3 py-2 text-sm" required>
                            <option value="">Select a room</option>
                            {rooms.map(room => (
                                <option key={room.id} value={room.id}>
                                    Room {room.roomNumber} — ${room.nightlyRate}/night
                                </option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm text-gray-600 mb-1">Check-in</label>
                        <input type="date" name="checkInDate" value={form.checkInDate} onChange={handleChange} className="w-full border rounded px-3 py-2 text-sm" required />
                    </div>

                    <div>
                        <label className="block text-sm text-gray-600 mb-1">Check-out</label>
                        <input type="date" name="checkOutDate" value={form.checkOutDate} min={form.checkInDate || undefined} onChange={handleChange} className="w-full border rounded px-3 py-2 text-sm" required />
                    </div>

                    {isEditing && (
                        <div>
                            <label className="block text-sm text-gray-600 mb-1">Status</label>
                            <select name="status" value={form.status} onChange={handleChange} className="w-full border rounded px-3 py-2 text-sm" >
                                <option value="CONFIRMED">Confirmed</option>
                                <option value="CANCELLED">Cancelled</option>
                            </select>
                        </div>
                    )}

                    {error && <p className="text-sm text-red-600">{error}</p>}

                    <div className="flex justify-end gap-3 mt-2">
                        <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-black">
                            Cancel
                        </button>
                        <button type="submit" className="px-4 py-2 text-sm bg-black text-white rounded hover:bg-gray-800">
                            {isEditing ? 'Save' : 'Create'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    )
}

export default ReservationModal