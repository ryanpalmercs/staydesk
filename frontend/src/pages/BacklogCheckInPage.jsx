import { useEffect, useState } from "react"
import { differenceInCalendarDays, parseISO } from "date-fns"
import { getRooms } from "../api/roomApi"
import { getRoomTypes } from "../api/roomTypeApi"
import { backlogCheckIn } from "../api/reservationApi"
import { formatPhone } from "../utils/phone"

const emptyForm = {
    roomId: '',
    firstName: '',
    lastName: '',
    email: '',
    phoneNumber: '',
    checkInDate: '',
    checkOutDate: ''
}

function BacklogCheckInPage() {
    const [rooms, setRooms] = useState([])
    const [roomTypes, setRoomTypes] = useState([])
    const [form, setForm] = useState(emptyForm)
    const [phoneFocused, setPhoneFocused] = useState(false)
    const [error, setError] = useState('')
    const [success, setSuccess] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    const availableRooms = rooms.filter(r => r.status === 'AVAILABLE').sort((a, b) => a.roomNumber - b.roomNumber)
    const roomTypeName = id => roomTypes.find(rt => rt.id === id)?.name.replace('_', ' ') ?? ''

    const totalNights = form.checkInDate && form.checkOutDate
        ? differenceInCalendarDays(parseISO(form.checkOutDate), parseISO(form.checkInDate))
        : 0

    const rateType = totalNights > 0 && totalNights % 7 === 0 ? 'WEEKLY_7'
        : totalNights > 0 && totalNights % 5 === 0 ? 'WEEKLY_5'
            : 'NIGHTLY'

    useEffect(() => {
        getRooms().then(res => setRooms(res.data ?? [])).catch(err => console.error(err))
        getRoomTypes().then(res => setRoomTypes(res.data ?? [])).catch(err => console.error(err))
    }, [])

    function handleChange(e) {
        setForm({ ...form, [e.target.name]: e.target.value })
    }

    async function handleSubmit(e) {
        e.preventDefault()
        setError('')
        setSuccess(null)
        setSubmitting(true)

        try {
            const response = await backlogCheckIn({
                roomId: Number(form.roomId),
                firstName: form.firstName,
                lastName: form.lastName,
                email: form.email || null,
                phoneNumber: form.phoneNumber || null,
                checkInDate: form.checkInDate,
                checkOutDate: form.checkOutDate,
                rateType
            })

            setSuccess(response.data)
            setForm(emptyForm)
            getRooms().then(res => setRooms(res.data ?? [])).catch(err => console.error(err))
        } catch (err) {
            const data = err.response?.data
            setError(typeof data === 'string' && data ? data : 'Failed to record backlog check-in')
            console.error(err)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div>
            <div className="page-header mb-6">
                <h1 className="section-title">Backlog Check-In</h1>
            </div>

            <p className="text-sm text-muted mb-6 max-w-2xl">
                Records a guest as already checked in without going through the normal reservation flow — no
                availability hold, no folio charge, no payment is taken. Use this to bring Staydesk back in line
                with reality after staff have been operating off paper (e.g. a system outage).
            </p>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4 max-w-2xl">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-4">
                    <div>
                        <label className="block text-sm text-muted mb-1">Room</label>
                        <select name="roomId" value={form.roomId} onChange={handleChange} className="filter-input" required>
                            <option value="">Select a room</option>
                            {availableRooms.map(room => (
                                <option key={room.id} value={room.id}>
                                    Room {room.roomNumber} — {roomTypeName(room.roomTypeId)}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="flex gap-2">
                        <div className="flex-1">
                            <label className="block text-sm text-muted mb-1">First Name</label>
                            <input name="firstName" value={form.firstName} onChange={handleChange} className="filter-input" required />
                        </div>
                        <div className="flex-1">
                            <label className="block text-sm text-muted mb-1">Last Name</label>
                            <input name="lastName" value={form.lastName} onChange={handleChange} className="filter-input" required />
                        </div>
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Email <span className="text-muted">(optional)</span></label>
                        <input type="email" name="email" value={form.email} onChange={handleChange} className="filter-input" placeholder="Leave blank if unknown" />
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Phone <span className="text-muted">(optional)</span></label>
                        <input
                            name="phoneNumber"
                            value={phoneFocused ? form.phoneNumber : formatPhone(form.phoneNumber)}
                            onChange={e => setForm({ ...form, phoneNumber: e.target.value.replace(/\D/g, '').slice(0, 10) })}
                            onFocus={() => setPhoneFocused(true)}
                            onBlur={() => setPhoneFocused(false)}
                            className="filter-input"
                            placeholder="Leave blank if unknown"
                        />
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Check-In Date</label>
                        <input type="date" name="checkInDate" value={form.checkInDate} onChange={handleChange} className="filter-input" required />
                    </div>

                    <div>
                        <label className="block text-sm text-muted mb-1">Check-Out Date</label>
                        <input type="date" name="checkOutDate" value={form.checkOutDate} onChange={handleChange} className="filter-input" required />
                    </div>
                </div>

                {error && <p className="text-sm text-error">{error}</p>}
                {success && (
                    <p className="text-sm text-green">
                        Recorded — confirmation code {success.confirmationCode}, room set to occupied.
                    </p>
                )}

                <div className="flex justify-end">
                    <button type="submit" className="btn btn-primary" disabled={submitting}>
                        {submitting ? 'Recording...' : 'Record Check-In'}
                    </button>
                </div>
            </form>
        </div>
    )
}

export default BacklogCheckInPage
