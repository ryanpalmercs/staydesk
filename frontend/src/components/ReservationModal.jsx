import { useEffect, useRef, useState } from "react"
import { createReservation, updateReservation } from "../api/reservationApi"
import { getRooms } from "../api/roomApi"
import { createGuest, getGuests } from "../api/guestApi"
import { getRates } from "../api/rateApi"
import { getFolioByReservationId, addFolioItem } from "../api/folioApi"
import { getExtras } from "../api/extrasApi"

function ReservationModal({ reservation, onSaved, onClose }) {
    const isEditing = reservation != null
    const canAddExtras = isEditing && reservation.status === 'CHECKED_IN'

    const [rooms, setRooms] = useState([])
    const [guests, setGuests] = useState([])
    const [rates, setRates] = useState([])
    const [getsCount, setGuestCount] = useState('1')
    const [guestMode, setGuestMode] = useState('search')
    let [form, setForm] = useState({
        guestId: reservation?.guestId ?? '',
        roomId: reservation?.roomId ?? '',
        rateType: reservation?.rateType ?? 'NIGHTLY',
        guestCount: reservation?.guestCount ?? '1',
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
    const initialFormRef = useRef(form)
    const isDirty = JSON.stringify(form) !== JSON.stringify(initialFormRef.current)

    const [error, setError] = useState(null)

    const [showExtras, setShowExtras] = useState(false)
    const [folioId, setFolioId] = useState(null)
    const [extras, setExtras] = useState([])
    const [selectedExtraId, setSelectedExtraId] = useState('')
    const [extraQuantity, setExtraQuantity] = useState(1)
    const [extraMessage, setExtraMessage] = useState(null)

    const selectedRate = rates.find(r => r.rateType === form.rateType && r.guestCount === Number(form.guestCount))

    useEffect(() => {
        getRooms().then(res => setRooms(res.data ?? [])),
            getGuests().then(res => setGuests(res.data ?? [])),
            getRates().then(res => setRates(res.data ?? []))

        if (canAddExtras) {
            getFolioByReservationId(reservation.id).then(res => setFolioId(res.data.id))
            getExtras().then(res => setExtras(res.data ?? []))
        }
    }, [])

    async function handleAddExtra() {
        if (!selectedExtraId || !folioId) return

        setExtraMessage(null)

        try {
            await addFolioItem(folioId, Number(selectedExtraId), Number(extraQuantity))
            setExtraMessage('Added.')
            setSelectedExtraId('')
            setExtraQuantity(1)
        } catch (err) {
            setExtraMessage(err.response?.status === 409 ? 'Folio is closed.' : 'Failed to add item.')
        }
    }

    function handleChange(e) {
        setForm({ ...form, [e.target.name]: e.target.value })
    }

    function handleRateChange(e) {
        const newRateType = e.target.value
        setForm({
            ...form,
            rateType: newRateType,
            guestCount: newRateType === 'NIGHTLY' && form.guestCount === '3' ? '2' : form.guestCount
        })
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
                console.dubug(submittedForm)
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
            <div className="bg-warm-white rounded-lg p-6 w-full max-w-md shadow-lg border-t-4 border-rust">
                <h2 className="text-lg text-charcoal font-semibold mb-4">
                    {isEditing ? 'Edit Reservation' : 'New Reservation'}
                </h2>

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <div>
                        <div className="flex items-baseline gap-2">
                            <label className="text-sm text-muted">Guest</label>
                            <button type="button" onClick={onGuestModeChange} className="text-sm font-medium text-rust hover:text-rust-light">
                                {guestMode === 'search' ? 'New Guest' : 'Select Existing'}
                            </button>
                        </div>
                        {guestMode === 'search' ? (
                            <select name="guestId" value={form.guestId} onChange={handleChange} className="filter-input" required>
                                <option value="">Select a guest</option>
                                {guests.map(guest => (
                                    <option key={guest.id} value={guest.id}>
                                        {guest.firstName} {guest.lastName}
                                    </option>
                                ))}
                            </select>
                        ) : (
                            <div className="flex flex-col gap-2">
                                <input name="firstName" placeholder="First name" onChange={handleGuestFieldChange} className="filter-input" required />
                                <input name="lastName" placeholder="Last name" onChange={handleGuestFieldChange} className="filter-input" required />
                                <input name="email" placeholder="Email" onChange={handleGuestFieldChange} className="filter-input" required />
                                <input name="phoneNumber" placeholder="Phone (10 digits)" onChange={handleGuestFieldChange} className="filter-input" required />
                            </div>
                        )}
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Rate Type</label>
                        <select name="rateType" value={form.rateType} onChange={handleRateChange} className="filter-input" required>
                            <option value="NIGHTLY">Nightly</option>
                            <option value="WEEKLY_5">Weekly (5-night)</option>
                            <option value="WEEKLY_7">Weekly (7-night)</option>
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Number of Guests</label>
                        <select name="guestCount" value={form.guestCount} onChange={handleChange} className="filter-input" required>
                            <option value="1">1</option>
                            <option value="2">2</option>
                            {form.rateType !== 'NIGHTLY' && <option value="3">3</option>}
                        </select>
                    </div>

                    {selectedRate &&
                        <div className="flex items-baseline gap-2">
                            <label className="block text-sm text-muted mb-1">Rate</label>
                            <p className="text-sm font-medium text-charcoal mt-1">${selectedRate.amount}</p>
                        </div>
                    }

                    <div>
                        <label className="block text-sm text-muted mb-1">Room</label>
                        <select name="roomId" value={form.roomId} onChange={handleChange} className="filter-input" required>
                            <option value="">Select a room</option>
                            {rooms.map(room => (
                                <option key={room.id} value={room.id}>
                                    Room {room.roomNumber} — ${room.nightlyRate}/night
                                </option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Check-in</label>
                        <input type="date" name="checkInDate" value={form.checkInDate} onChange={handleChange} className="filter-input" required />
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Check-out</label>
                        <input type="date" name="checkOutDate" value={form.checkOutDate} min={form.checkInDate || undefined} onChange={handleChange} className="filter-input" required />
                    </div>

                    {isEditing && (
                        <div>
                            <label className="block text-sm text-muted mb-1">Status</label>
                            <select name="status" value={form.status} onChange={handleChange} className="filter-input" >
                                <option value="CONFIRMED">Confirmed</option>
                                <option value="CANCELLED">Cancelled</option>
                            </select>
                        </div>
                    )}

                    {canAddExtras && (
                        <div>
                            <button type="button" onClick={() => setShowExtras(!showExtras)} className="text-sm font-medium text-rust hover:text-rust-light">
                                {showExtras ? 'Hide Extras' : 'Add Extras'}
                            </button>

                            {showExtras && (
                                <div className="flex gap-2 items-end mt-2">
                                    <select value={selectedExtraId} onChange={e => setSelectedExtraId(e.target.value)} className="filter-input flex-1">
                                        <option value="">Select an extra...</option>
                                        {extras.map(extra => (
                                            <option key={extra.id} value={extra.id}>{extra.name} (${extra.price.toFixed(2)})</option>
                                        ))}
                                    </select>
                                    <input type="number" min="1" value={extraQuantity} onChange={e => setExtraQuantity(e.target.value)} className="filter-input w-20" />
                                    <button type="button" onClick={handleAddExtra} className="btn btn-secondary">Add</button>
                                </div>
                            )}

                            {extraMessage && <p className="text-sm text-muted mt-1">{extraMessage}</p>}
                        </div>
                    )}

                    {error && <p className="text-sm text-rust">{error}</p>}

                    <div className="flex justify-end gap-3 mt-2">
                        <button type="button" onClick={onClose} className="btn btn-secondary">
                            Cancel
                        </button>
                        <button type="submit" className="btn btn-primary" disabled={isEditing && !isDirty}>
                            {isEditing ? 'Save' : 'Create'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    )
}

export default ReservationModal