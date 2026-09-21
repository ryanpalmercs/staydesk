import { useEffect, useRef, useState } from "react"
import { assignRoom, createReservation, createMultiRoomReservation, getCheckInEstimate, getReservationEstimate, getReservationEstimateWithExtras, payFullStayNow, payFullStayNowTerminal, updateReservation } from "../api/reservationApi"
import { getRoomTypes, getUnavailableRoomTypeIds } from "../api/roomTypeApi"
import { getRoom } from "../api/roomApi"
import { createGuest, getGuests, updateGuest } from "../api/guestApi"
import { formatPhone } from "../utils/phone"
import { formatGuestName } from "../utils/guestName"
import { getFolioByReservationId, addFolioItem } from "../api/folioApi"
import { getFeatureFlags } from "../api/featureFlagsApi"
import { getExtras } from "../api/extrasApi"
import AcceptJsCardForm from "./AcceptJsCardForm"
import PaymentMethodStep from "./PaymentMethodStep"
import { getPropertySetting } from "../api/settingsApi"
import ReservationDatePicker from "./ReservationDatePicker"
import { differenceInCalendarDays, parseISO } from "date-fns"
import { CircleMinus, CirclePlus, Trash2 } from "lucide-react"
import Modal from "./Modal"
import AssignRoomModal from "./AssignRoomModal"
import MoveRoomModal from "./MoveRoomModal"

function Stepper({ label, value, min, max, onChange }) {
    return (
        <div flex items-center justify-center>
            <label className="block text-sm text-muted mb-1">{label}</label>
            <div className="flex items-center justify-center gap-3">
                <button type="button" onClick={() => onChange(Math.max(min, value - 1))} disabled={value <= min}
                    className="w-8 h-8 flex items-center justify-center p-0 color-tan" aria-label={`Decrease ${label}`}>
                    <CircleMinus size={18} />
                </button>
                <span className="w-6 text-center">{value}</span>
                <button type="button" onClick={() => onChange(Math.min(max, value + 1))} disabled={value >= max}
                    className="w-8 h-8 flex items-center justify-center p-0 color-tan" aria-label={`Increase ${label}`}>
                    <CirclePlus size={18} />
                </button>
            </div>
        </div>
    )
}

function ReservationModal({ reservation, onSaved, onClose }) {
    const isEditing = reservation != null
    const canAddExtras = isEditing && reservation.status === 'CHECKED_IN'

    const [roomTypes, setRoomTypes] = useState([])
    const [unavailableRoomTypeIds, setUnavailableRoomTypeIds] = useState([])
    const [multiRoomBookingEnabled, setMultiRoomBookingEnabled] = useState(false)
    const [guests, setGuests] = useState([])
    const [guestFormError, setGuestFormError] = useState(null)
    const [creatingGuest, setCreatingGuest] = useState(false)
    let [form, setForm] = useState({
        guestId: reservation?.guestId ?? '',
        roomTypeId: reservation?.roomTypeId ?? '',
        adults: reservation?.guestCount ?? 1,
        children: 0,
        checkInDate: reservation?.checkInDate ?? '',
        checkOutDate: reservation?.checkOutDate ?? '',
        status: reservation?.status ?? 'CONFIRMED',
        channel: reservation?.channel ?? null
    })

    const [guestForm, setGuestForm] = useState({
        firstName: '',
        lastName: '',
        email: '',
        phoneNumber: '',
        smsConsent: false,
        guestType: 'INDIVIDUAL'
    })
    const initialFormRef = useRef(form)
    const isDirty = JSON.stringify(form) !== JSON.stringify(initialFormRef.current)

    const [error, setError] = useState(null)

    const [showExtras, setShowExtras] = useState(false)
    const [folioId, setFolioId] = useState(null)
    const [assignedRoom, setAssignedRoom] = useState(null)
    const [dateLeftInset, setDateLeftInset] = useState(0)
    const [extras, setExtras] = useState([])
    const [selectedExtraId, setSelectedExtraId] = useState('')
    const [extraQuantity, setExtraQuantity] = useState(1)
    const [extraMessage, setExtraMessage] = useState(null)
    const [stagedExtras, setStagedExtras] = useState([])

    const [step, setStep] = useState(isEditing ? 'form' : 'choice')
    const [guestStepOrigin, setGuestStepOrigin] = useState('choice')
    const [guestSearchQuery, setGuestSearchQuery] = useState('')
    const [editingGuestInfo, setEditingGuestInfo] = useState(false)
    const [pendingForm, setPendingForm] = useState(null)
    const [provider, setProvider] = useState(null)
    const paymentReady = provider === 'authorizenet'
    const [payTimingChoice, setPayTimingChoice] = useState(null)
    const [payNowReservationId, setPayNowReservationId] = useState(null)
    const [payNowReservation, setPayNowReservation] = useState(null)
    const [payNowAmount, setPayNowAmount] = useState(null)
    const [selectedRoomId, setSelectedRoomId] = useState('')

    const isFutureWalkIn = form.channel === 'WALK_IN' && form.checkInDate
        ? differenceInCalendarDays(parseISO(form.checkInDate), new Date()) > 0
        : false

    const selectedGuest = guests.find(g => g.id === Number(form.guestId))
    const flaggedMatch = selectedGuest?.flagged ? selectedGuest : null

    const newGuestFlaggedMatch = guests.find(g => g.flagged && (
        (guestForm.email && g.email && g.email.toLowerCase() === guestForm.email.toLowerCase()) ||
        (guestForm.phoneNumber && g.phoneNumber === guestForm.phoneNumber)
    ))

    const visibleGuests = [...guests]
        .filter(g => formatGuestName(g).toLowerCase().includes(guestSearchQuery.toLowerCase()))
        .sort((a, b) => formatGuestName(a).localeCompare(formatGuestName(b)))

    const totalNights = form.checkInDate && form.checkOutDate
        ? differenceInCalendarDays(parseISO(form.checkOutDate), parseISO(form.checkInDate))
        : 0

    const rateType = totalNights >= 7 ? 'WEEKLY_7'
        : totalNights >= 5 ? 'WEEKLY_5'
            : 'NIGHTLY'
    const maxGuestCount = 4
    const guestCount = form.adults + form.children

    const [estimate, setEstimate] = useState(null)

    const [roomLines, setRoomLines] = useState([{ roomTypeId: '', quantity: 1 }])

    function addRoomLine() {
        setRoomLines(lines => [...lines, { roomTypeId: '', quantity: 1 }])
    }

    function removeRoomLine(index) {
        setRoomLines(lines => lines.filter((_, i) => i !== index))
    }

    function updateRoomLine(index, field, value) {
        setRoomLines(lines => lines.map((line, i) => i === index ? { ...line, [field]: value } : line))
    }

    function isMultiRoom(lines) {
        return lines.length > 1 || lines.some(l => Number(l.quantity) > 1)
    }

    const totalRoomCount = roomLines.reduce((sum, l) => sum + (Number(l.quantity) || 0), 0)

    useEffect(() => {
        getRoomTypes().then(res => setRoomTypes(res.data ?? [])),
            getGuests().then(res => setGuests(res.data ?? []))

        getFeatureFlags().then(res => setMultiRoomBookingEnabled(res.data.multiRoomBookingEnabled)).catch(() => setMultiRoomBookingEnabled(false))

        if (canAddExtras) {
            getFolioByReservationId(reservation.id).then(res => setFolioId(res.data.id))
        }

        if (canAddExtras || !isEditing) {
            getExtras().then(res => setExtras(res.data ?? []))
        }

        if (!isEditing) {
            getPropertySetting('payment_provider').then(res => {
                setProvider(res.data.value)
            })
        }

        if (isEditing && reservation.roomId != null) {
            getRoom(reservation.roomId).then(res => setAssignedRoom(res.data)).catch(() => setAssignedRoom(null))
        }
    }, [])

    useEffect(() => {
        if (form.adults + form.children <= maxGuestCount) {
            return
        }
        setForm(f => ({ ...f, children: Math.max(0, maxGuestCount - f.adults) }))
    }, [maxGuestCount])

    useEffect(() => {
        if (!form.checkInDate || !form.checkOutDate) {
            setEstimate(null)
            return
        }
        let cancelled = false
        getReservationEstimateWithExtras({
            rateType, guestCount, checkInDate: form.checkInDate, checkOutDate: form.checkOutDate,
            guestId: form.guestId || undefined,
            extras: stagedExtras.map(item => ({ extraId: item.extraId, quantity: item.quantity }))
        })
            .then(res => {
                if (!cancelled) {
                    setEstimate(res.data)
                }
            })
            .catch(() => {
                if (!cancelled) {
                    setEstimate(null)
                }
            })
        return () => { cancelled = true }
    }, [rateType, guestCount, form.checkInDate, form.checkOutDate, form.guestId, stagedExtras])

    useEffect(() => {
        if (!form.checkInDate || !form.checkOutDate) {
            setUnavailableRoomTypeIds([])
            return
        }
        let cancelled = false
        getUnavailableRoomTypeIds(form.checkInDate, form.checkOutDate, reservation?.id)
            .then(res => { if (!cancelled) setUnavailableRoomTypeIds(res.data ?? []) })
            .catch(() => { if (!cancelled) setUnavailableRoomTypeIds([]) })
        return () => { cancelled = true }
    }, [form.checkInDate, form.checkOutDate])

    async function handleAddExtra() {
        if (!selectedExtraId) return

        if (canAddExtras) {
            if (!folioId) return

            setExtraMessage(null)

            try {
                await addFolioItem(folioId, Number(selectedExtraId), Number(extraQuantity))
                setExtraMessage('Added.')
                setSelectedExtraId('')
                setExtraQuantity(1)
            } catch (err) {
                setExtraMessage(err.response?.status === 409 ? 'Folio is closed.' : 'Failed to add item.')
            }
            return
        }

        const extra = extras.find(e => e.id === Number(selectedExtraId))
        if (!extra) return

        setStagedExtras(prev => [...prev, {
            extraId: extra.id, name: extra.name, price: extra.price, billingType: extra.billingType,
            quantity: Number(extraQuantity)
        }])
        setSelectedExtraId('')
        setExtraQuantity(1)
    }

    function removeStagedExtra(index) {
        setStagedExtras(prev => prev.filter((_, i) => i !== index))
    }

    function stagedExtraSelections() {
        return stagedExtras.map(item => ({ extraId: item.extraId, quantity: item.quantity }))
    }

    function handleChange(e) {
        setForm(f => ({ ...f, [e.target.name]: e.target.value }))
    }

    function handleGuestFieldChange(e) {
        setGuestForm({ ...guestForm, [e.target.name]: e.target.value })
    }

    async function handleCreateGuest(e) {
        e.preventDefault()
        setGuestFormError(null)
        setCreatingGuest(true)

        try {
            const res = await createGuest(guestForm)
            const guestsRes = await getGuests()
            setGuests(guestsRes.data)
            setForm(f => ({ ...f, guestId: res.data.id }))
            setGuestForm({ firstName: '', lastName: '', email: '', phoneNumber: '', smsConsent: false, guestType: 'INDIVIDUAL' })
            setStep('form')
        } catch (err) {
            if (err.response?.status === 400) {
                setGuestFormError('Please check the fields — phone must be 10 digits, and last name is required for individuals.')
            } else if (err.response?.status === 409) {
                setGuestFormError('A guest with that email already exists.')
            } else {
                setGuestFormError('Failed to create guest.')
            }
        }

        setCreatingGuest(false)
    }

    function startEditingGuestInfo() {
        setGuestForm({
            firstName: selectedGuest.firstName,
            lastName: selectedGuest.lastName,
            email: selectedGuest.email ?? '',
            phoneNumber: selectedGuest.phoneNumber,
            smsConsent: selectedGuest.smsConsent,
            guestType: selectedGuest.guestType ?? 'INDIVIDUAL'
        })
        setGuestFormError(null)
        setEditingGuestInfo(true)
    }

    async function handleUpdateGuestInfo(e) {
        e.preventDefault()
        setGuestFormError(null)
        setCreatingGuest(true)

        try {
            await updateGuest(selectedGuest.id, guestForm)
            const guestsRes = await getGuests()
            setGuests(guestsRes.data)
            setEditingGuestInfo(false)
        } catch (err) {
            if (err.response?.status === 400) {
                setGuestFormError('Please check the fields — phone must be 10 digits and email must be valid.')
            } else {
                setGuestFormError('Failed to update guest.')
            }
        }

        setCreatingGuest(false)
    }

    function isToday(dateString) {
        const now = new Date()
        const todayString = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
        return dateString === todayString
    }

    async function handleSubmit(e) {
        e.preventDefault()
        setError(null)

        if (!form.guestId) {
            setError('Please select a guest.')
            return
        }

        if (isEditing || !multiRoomBookingEnabled) {
            if (!form.roomTypeId) {
                setError('Please select a room type.')
                return
            }
        } else if (roomLines.some(l => !l.roomTypeId)) {
            setError('Please select a room type for each room.')
            return
        }

        if (unavailableRoomTypeIds.includes(Number(form.roomTypeId))) {
            setError('No room of this type is available for the selected dates.')
            return
        }

        if (unavailableRoomTypeIds.includes(Number(form.roomTypeId))) {
            setError('No room of this type is available for the selected dates.')
            return
        }

        if (form.checkOutDate <= form.checkInDate) {
            setError('Check-out date must be after check-in date.')
            return
        }

        if (!form.channel) {
            setError('Please select how this reservation is being booked.')
            return
        }

        const { adults, children, roomTypeId, ...rest } = form
        const isFutureWalkIn = form.channel === 'WALK_IN' && !isToday(form.checkInDate)

        if (isEditing) {
            try {
                await updateReservation(reservation.id, { ...reservation, ...rest, roomTypeId, rateType, guestCount })
                onSaved()
            } catch (err) {
                if (err.response?.status === 400) setError('No room of this type is available for the selected dates.')
                else if (err.response?.status === 404) setError('Room type not found.')
                else setError('Something went wrong.')
            }
            return
        }

        const multiRoom = multiRoomBookingEnabled && isMultiRoom(roomLines)
        const channel = isFutureWalkIn ? 'PHONE' : form.channel

        const basePayload = {
            guestId: form.guestId,
            checkInDate: form.checkInDate,
            checkOutDate: form.checkOutDate,
            rateType,
            guestCount,
            channel
        }

        // Multi-room booking is feature-flagged off by default (not yet integrated with the
        // assign-room/pay-timing flow below) - see #{{multi-room-pay-timing-followup}}. While
        // disabled, this branch is unreachable since roomLines never grows past one line.
        if (multiRoom) {
            const payload = { ...basePayload, rooms: roomLines.map(l => ({ roomTypeId: Number(l.roomTypeId), quantity: Number(l.quantity) })) }

            if (form.channel === 'WALK_IN' && !isFutureWalkIn) {
                try {
                    await createMultiRoomReservation({ ...payload, roomPaymentMethodId: null, extras: stagedExtraSelections() })
                    onSaved()
                } catch (err) {
                    setError(err.response?.status === 400 ? 'No room of this type is available for the selected dates.' : 'Something went wrong.')
                }
                return
            }

            if (!paymentReady) {
                setError('Payment provider is not connected. Check Settings.')
                return
            }
            setPendingForm({ ...payload, multiRoom: true })
            setStep('payment')
            return
        }

        const payload = { ...basePayload, roomTypeId: Number(form.roomTypeId) }

        if (form.channel === 'WALK_IN' && !isFutureWalkIn) {
            try {
                const res = await createReservation({ ...payload, roomPaymentMethodId: null, extras: stagedExtraSelections() })
                onSaved(res.data.id)
            } catch (err) {
                setError(err.response?.status === 400 ? 'No room of this type is available for the selected dates.' : 'Something went wrong.')
            }
            return
        }

        // A future-dated walk-in or any phone booking has no guest present yet, so staff
        // can optionally lock in a specific room before working through payment timing.
        setPendingForm({ ...payload, multiRoom: false })
        setPayTimingChoice(null)
        setSelectedRoomId('')
        setStep('assign-room')
    }


    // The room (if any) was picked earlier in the assign-room step, before the reservation itself
    // existed - assignRoom runs now that it does. A room-assignment failure here (e.g. someone else
    // just took it) doesn't block finishing: the reservation is already secured either way, and the
    // room can still be assigned later from the reservation list or calendar.
    async function completeReservation(created) {
        if (selectedRoomId) {
            try {
                await assignRoom(created.id, Number(selectedRoomId))
            } catch (err) {
                console.error('Failed to assign room:', err)
            }
        }
        onSaved()
    }


    // The room (if any) was picked earlier in the assign-room step, before the reservation itself
    // existed - assignRoom runs now that it does. A room-assignment failure here (e.g. someone else
    // just took it) doesn't block finishing: the reservation is already secured either way, and the
    // room can still be assigned later from the reservation list or calendar.
    async function completeReservation(created) {
        if (selectedRoomId) {
            try {
                await assignRoom(created.id, Number(selectedRoomId))
            } catch (err) {
                console.error('Failed to assign room:', err)
            }
        }
        onSaved()
    }

    async function handleCapture(paymentMethodId) {
        try {
            const { multiRoom, ...payload } = pendingForm
            if (multiRoom) {
                await createMultiRoomReservation({ ...payload, roomPaymentMethodId: paymentMethodId, extras: stagedExtraSelections() })
                onSaved()
            } else {
                const res = await createReservation({ ...payload, roomPaymentMethodId: paymentMethodId, extras: stagedExtraSelections() })
                completeReservation(res.data)
            }
        } catch (err) {
            setStep('form')
            if (err.response?.status === 400) {
                setError('No room of this type is available for the selected dates.')
            } else {
                setError('Something went wrong.')
            }
        }
    }

    async function handlePayNowChosen() {
        if (form.channel === 'WALK_IN') {
            try {
                const res = await createReservation({ ...pendingForm, roomPaymentMethodId: null, extras: stagedExtraSelections() })
                setPayNowReservationId(res.data.id)
                setPayNowReservation(res.data)
                const estimateRes = await getCheckInEstimate(res.data.id)
                setPayNowAmount(estimateRes.data.total)
                setStep('pay-now')
            } catch (err) {
                setStep('form')
                setError(err.response?.status === 400 ? 'No room of this type is available for the selected dates.' : 'Something went wrong.')
            }
            return
        }

        // PHONE: paying now still means collecting a card-not-present token before creating,
        // same as every phone booking did before this choice existed.
        if (!paymentReady) {
            setStep('form')
            setError('Payment provider is not connected. Check Settings.')
            return
        }
        setStep('payment')
    }

    async function handlePayLaterChosen() {
        try {
            // No charge now; the room total is collected via the card-present terminal once the
            // guest actually arrives and checks in.
            const res = await createReservation({ ...pendingForm, roomPaymentMethodId: null, extras: stagedExtraSelections() })
            completeReservation(res.data)
        } catch (err) {
            setStep('form')
            setError(err.response?.status === 400 ? 'No room of this type is available for the selected dates.' : 'Something went wrong.')
        }
    }

    // Its own separate modal, not a step inside this one - the reservation form closes, this
    // opens in its place, and picking a room (or skipping) hands control back for pay-timing to
    // open next, rather than nesting one dialog inside another.
    if (step === 'assign-room') {
        return (
            <AssignRoomModal
                roomTypeId={form.roomTypeId}
                checkInDate={form.checkInDate}
                checkOutDate={form.checkOutDate}
                onSaved={roomId => { setSelectedRoomId(String(roomId)); setStep('pay-timing') }}
                onClose={() => { setSelectedRoomId(''); setStep('pay-timing') }}
                onBack={() => setStep('form')}
            />
        )
    }

    // "Change Room" on an existing reservation - same "separate modal, not nested" treatment as
    // the booking flow's own room picker. A standalone action, not bundled into the rest of the
    // edit form's save: closes the whole edit modal on success so the parent refetches fresh data,
    // same as the Move/Assign Room entry points elsewhere in the app.
    if (step === 'change-room') {
        return (
            <MoveRoomModal
                reservation={reservation}
                onSaved={onSaved}
                onClose={() => setStep('form')}
            />
        )
    }

    async function handlePayNowChosen() {
        if (form.channel === 'WALK_IN') {
            try {
                const res = await createReservation({ ...pendingForm, roomPaymentMethodId: null, extras: stagedExtraSelections() })
                setPayNowReservationId(res.data.id)
                setPayNowReservation(res.data)
                const estimateRes = await getCheckInEstimate(res.data.id)
                setPayNowAmount(estimateRes.data.total)
                setStep('pay-now')
            } catch (err) {
                setStep('form')
                setError(err.response?.status === 400 ? 'No room of this type is available for the selected dates.' : 'Something went wrong.')
            }
            return
        }

        // PHONE: paying now still means collecting a card-not-present token before creating,
        // same as every phone booking did before this choice existed.
        if (!paymentReady) {
            setStep('form')
            setError('Payment provider is not connected. Check Settings.')
            return
        }
        setStep('payment')
    }

    async function handlePayLaterChosen() {
        try {
            // No charge now; the room total is collected via the card-present terminal once the
            // guest actually arrives and checks in.
            const res = await createReservation({ ...pendingForm, roomPaymentMethodId: null, extras: stagedExtraSelections() })
            completeReservation(res.data)
        } catch (err) {
            setStep('form')
            setError(err.response?.status === 400 ? 'No room of this type is available for the selected dates.' : 'Something went wrong.')
        }
    }

    // Its own separate modal, not a step inside this one - the reservation form closes, this
    // opens in its place, and picking a room (or skipping) hands control back for pay-timing to
    // open next, rather than nesting one dialog inside another.
    if (step === 'assign-room') {
        return (
            <AssignRoomModal
                roomTypeId={form.roomTypeId}
                checkInDate={form.checkInDate}
                checkOutDate={form.checkOutDate}
                onSaved={roomId => { setSelectedRoomId(String(roomId)); setStep('pay-timing') }}
                onClose={() => { setSelectedRoomId(''); setStep('pay-timing') }}
                onBack={() => setStep('form')}
            />
        )
    }

    // "Change Room" on an existing reservation - same "separate modal, not nested" treatment as
    // the booking flow's own room picker. A standalone action, not bundled into the rest of the
    // edit form's save: closes the whole edit modal on success so the parent refetches fresh data,
    // same as the Move/Assign Room entry points elsewhere in the app.
    if (step === 'change-room') {
        return (
            <MoveRoomModal
                reservation={reservation}
                onSaved={onSaved}
                onClose={() => setStep('form')}
            />
        )
    }

    return (
        <Modal onClose={onClose} size="reservation" scrollable padded={false} isDirty={isDirty}>
            <h2 className="text-lg text-black font-semibold px-6 pt-6 pb-4">
                {step === 'payment' ? 'Card Details'
                    : step === 'pay-now' ? 'Charge for Stay'
                        : step === 'pay-timing' ? 'How Should This Stay Be Paid?'
                            : step === 'choice' ? 'New or Returning Guest?'
                                : step === 'guestList' ? 'Select Guest'
                                    : step === 'newGuest' ? 'New Guest'
                                        : step === 'confirmGuest' ? 'Confirm Guest Information'
                                            : isEditing ? `Edit Reservation for ${formatGuestName(selectedGuest)}` : `New Reservation for ${formatGuestName(selectedGuest)}`}
            </h2>

            {step === 'choice' && (
                <div className="flex flex-col flex-1 min-h-0 px-6 pb-6">
                    <div className="flex flex-col sm:flex-row gap-3">
                        <button type="button" onClick={() => { setGuestStepOrigin('choice'); setStep('newGuest') }} className="btn btn-secondary flex-1 py-4">
                            New Guest
                        </button>
                        <button type="button" onClick={() => { setGuestStepOrigin('choice'); setStep('guestList') }} className="btn btn-secondary flex-1 py-4">
                            Returning Guest
                        </button>
                    </div>

                    <div className="flex justify-end mt-auto pt-4 border-t border-tan">
                        <button type="button" onClick={onClose} className="btn btn-secondary">Cancel</button>
                    </div>
                </div>
            )}

            {step === 'guestList' && (
                <div className="flex flex-col flex-1 min-h-0 px-6 pb-6">
                    <div className="flex items-center gap-3 mb-4">
                        <input
                            type="text"
                            value={guestSearchQuery}
                            onChange={e => setGuestSearchQuery(e.target.value)}
                            placeholder="Search guests..."
                            className="filter-input flex-1"
                            autoFocus
                        />
                        <button type="button" onClick={() => setStep('newGuest')} className="btn btn-secondary">New Guest</button>
                    </div>

                    <div className="flex-1 min-h-0 overflow-y-auto flex flex-col gap-1">
                        {visibleGuests.map(g => (
                            <button
                                key={g.id}
                                type="button"
                                onClick={() => { setForm(f => ({ ...f, guestId: g.id })); setStep('confirmGuest') }}
                                className="filter-input flex justify-between items-center text-left hover:border-green"
                            >
                                <span>{formatGuestName(g)}</span>
                                {g.flagged && <span className="text-xs text-error font-medium">Flagged</span>}
                            </button>
                        ))}
                        {guests.length > 0 && visibleGuests.length === 0 && (
                            <p className="text-sm text-muted text-center py-4">No guests found.</p>
                        )}
                    </div>

                    <div className="flex justify-between mt-4 pt-4 border-t border-tan">
                        <button type="button" onClick={() => setStep(guestStepOrigin)} className="btn btn-secondary">Back</button>
                        <button type="button" onClick={onClose} className="btn btn-secondary">Cancel</button>
                    </div>
                </div>
            )}

            {step === 'newGuest' && (
                <div className="flex flex-col flex-1 min-h-0 px-6 pb-6">
                    <form onSubmit={handleCreateGuest} className="flex flex-col gap-4">
                        <div className="flex gap-2">
                            <button type="button" onClick={() => setGuestForm({ ...guestForm, guestType: 'INDIVIDUAL' })} className={`filter-btn${guestForm.guestType === 'INDIVIDUAL' ? ' active' : ''}`}>Individual</button>
                            <button type="button" onClick={() => setGuestForm({ ...guestForm, guestType: 'BUSINESS', lastName: '' })} className={`filter-btn${guestForm.guestType === 'BUSINESS' ? ' active' : ''}`}>Business Entity</button>
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                            {guestForm.guestType === 'BUSINESS' ? (
                                <input name="firstName" placeholder="Business name" value={guestForm.firstName} onChange={handleGuestFieldChange} className="filter-input w-full min-w-0 sm:col-span-2" required />
                            ) : (
                                <>
                                    <input name="firstName" placeholder="First name" value={guestForm.firstName} onChange={handleGuestFieldChange} className="filter-input w-full min-w-0" required />
                                    <input name="lastName" placeholder="Last name" value={guestForm.lastName} onChange={handleGuestFieldChange} className="filter-input w-full min-w-0" required />
                                </>
                            )}
                            <input name="email" placeholder="Email (optional)" value={guestForm.email} onChange={handleGuestFieldChange} className="filter-input w-full min-w-0" />
                            <input name="phoneNumber" placeholder="Phone (10 digits)" value={guestForm.phoneNumber} onChange={handleGuestFieldChange} className="filter-input w-full min-w-0" required />
                        </div>

                        <label className="flex items-start gap-2 text-sm text-muted">
                            <input
                                type="checkbox"
                                checked={guestForm.smsConsent}
                                onChange={e => setGuestForm({ ...guestForm, smsConsent: e.target.checked })}
                                className="mt-1"
                            />
                            <span>
                                I agree to receive SMS text messages from Martin House Motel about this reservation (door codes,
                                check-in/checkout confirmations). Message and data rates may apply. Reply STOP to opt out, HELP for
                                help. See our <a href="/sms-terms" target="_blank" rel="noopener noreferrer" className="text-green underline">SMS Terms</a>.
                            </span>
                        </label>

                        {newGuestFlaggedMatch && (
                            <div className="bg-red-100 text-red-700 text-sm rounded p-2">
                                Warning: this guest is flagged — {newGuestFlaggedMatch.flagReason}
                            </div>
                        )}

                        {guestFormError && <p className="text-sm text-error">{guestFormError}</p>}

                        <div className="flex justify-between gap-3 mt-2">
                            <button type="button" onClick={() => setStep(guestStepOrigin)} className="btn btn-secondary" disabled={creatingGuest}>
                                Back
                            </button>
                            <button type="submit" className="btn btn-primary" disabled={creatingGuest}>
                                {creatingGuest ? 'Adding...' : 'Add Guest'}
                            </button>
                        </div>
                    </form>
                </div>
            )}

            {step === 'confirmGuest' && selectedGuest && (
                <div className="flex flex-col flex-1 min-h-0 px-6 pb-6">
                    {!editingGuestInfo ? (
                        <div className="flex flex-col gap-4">
                            <div>
                                <label className="block text-sm text-muted mb-1">Name</label>
                                <p className="text-sm text-black">{formatGuestName(selectedGuest)}</p>
                            </div>
                            <div>
                                <label className="block text-sm text-muted mb-1">Email</label>
                                <p className="text-sm text-black">{selectedGuest.email || <span className="text-muted">No email on file</span>}</p>
                            </div>
                            <div>
                                <label className="block text-sm text-muted mb-1">Phone</label>
                                <p className="text-sm text-black">{formatPhone(selectedGuest.phoneNumber)}</p>
                            </div>

                            {selectedGuest.flagged && (
                                <div className="bg-red-100 text-red-700 text-sm rounded p-2">
                                    Warning: this guest is flagged — {selectedGuest.flagReason}
                                </div>
                            )}

                            <button type="button" onClick={startEditingGuestInfo} className="text-sm font-medium text-green hover:text-black self-start">
                                Edit Information
                            </button>
                        </div>
                    ) : (
                        <form onSubmit={handleUpdateGuestInfo} className="flex flex-col gap-4">
                            <div className="flex gap-2">
                                <button type="button" onClick={() => setGuestForm({ ...guestForm, guestType: 'INDIVIDUAL' })} className={`filter-btn${guestForm.guestType === 'INDIVIDUAL' ? ' active' : ''}`}>Individual</button>
                                <button type="button" onClick={() => setGuestForm({ ...guestForm, guestType: 'BUSINESS', lastName: '' })} className={`filter-btn${guestForm.guestType === 'BUSINESS' ? ' active' : ''}`}>Business Entity</button>
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                                {guestForm.guestType === 'BUSINESS' ? (
                                    <input name="firstName" placeholder="Business name" value={guestForm.firstName} onChange={handleGuestFieldChange} className="filter-input w-full min-w-0 sm:col-span-2" required />
                                ) : (
                                    <>
                                        <input name="firstName" placeholder="First name" value={guestForm.firstName} onChange={handleGuestFieldChange} className="filter-input w-full min-w-0" required />
                                        <input name="lastName" placeholder="Last name" value={guestForm.lastName} onChange={handleGuestFieldChange} className="filter-input w-full min-w-0" required />
                                    </>
                                )}
                                <input name="email" placeholder="Email (optional)" value={guestForm.email} onChange={handleGuestFieldChange} className="filter-input w-full min-w-0" />
                                <input name="phoneNumber" placeholder="Phone (10 digits)" value={guestForm.phoneNumber} onChange={handleGuestFieldChange} className="filter-input w-full min-w-0" required />
                            </div>

                            <label className="flex items-start gap-2 text-sm text-muted">
                                <input
                                    type="checkbox"
                                    checked={guestForm.smsConsent}
                                    onChange={e => setGuestForm({ ...guestForm, smsConsent: e.target.checked })}
                                    className="mt-1"
                                />
                                <span>Guest consents to receive SMS text messages (door codes, check-in/checkout confirmations).</span>
                            </label>

                            {guestFormError && <p className="text-sm text-error">{guestFormError}</p>}

                            <div className="flex justify-end gap-3">
                                <button type="button" onClick={() => setEditingGuestInfo(false)} className="btn btn-secondary" disabled={creatingGuest}>
                                    Cancel
                                </button>
                                <button type="submit" className="btn btn-primary" disabled={creatingGuest}>
                                    {creatingGuest ? 'Saving...' : 'Save'}
                                </button>
                            </div>
                        </form>
                    )}

                    {!editingGuestInfo && (
                        <div className="flex justify-between mt-4 pt-4 border-t border-tan">
                            <button type="button" onClick={() => setStep('guestList')} className="btn btn-secondary">Back</button>
                            <button type="button" onClick={() => setStep('form')} className="btn btn-primary">Continue</button>
                        </div>
                    )}
                </div>
            )}

            {step === 'form' && (
                <form onSubmit={handleSubmit} className="flex flex-col flex-1 min-h-0">
                    <div className="flex flex-col gap-2 overflow-y-auto px-6 pb-4 flex-1 min-h-0">
                        <div className="flex flex-col sm:flex-row gap-4 sm:gap-10" style={{ paddingLeft: dateLeftInset }}>
                            <div>
                                <label className="block text-sm text-muted mb-1">How is this being booked?</label>
                                <div className="flex justify-left gap-2">
                                    <button type="button" onClick={() => setForm(f => ({ ...f, channel: 'PHONE' }))} className={`filter-btn${form.channel === 'PHONE' ? ' active' : ''}`}>Phone</button>
                                    <button type="button" onClick={() => setForm(f => ({ ...f, channel: 'WALK_IN' }))} className={`filter-btn${form.channel === 'WALK_IN' ? ' active' : ''}`}>Walk-In</button>
                                </div>
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <Stepper
                                    label="Adults"
                                    value={form.adults}
                                    min={1}
                                    max={maxGuestCount - form.children}
                                    onChange={adults => setForm(f => ({ ...f, adults }))}
                                />
                                <Stepper
                                    label="Children"
                                    value={form.children}
                                    min={0}
                                    max={maxGuestCount - form.adults}
                                    onChange={children => setForm(f => ({ ...f, children }))}
                                />
                            </div>

                            <div>
                                <label className="block text-sm text-muted mb-1">{multiRoomBookingEnabled && !isEditing ? 'Rooms' : 'Room Type'}</label>
                                {isEditing ? (
                                    <div className="flex flex-col gap-1">
                                        <p className="text-sm text-black">
                                            {roomTypes.find(rt => rt.id === Number(form.roomTypeId))?.name.replace('_', ' ') ?? '—'}
                                            {assignedRoom && ` — Room ${assignedRoom.roomNumber}`}
                                        </p>
                                        {(reservation.status === 'CONFIRMED' || reservation.status === 'CHECKED_IN') && (
                                            <button type="button" onClick={() => setStep('change-room')} className="text-sm font-medium text-green hover:text-black self-start">
                                                Change Room
                                            </button>
                                        )}
                                    </div>
                                ) : multiRoomBookingEnabled ? (
                                    <div className="flex flex-col gap-2">
                                        {roomLines.map((line, index) => (
                                            <div key={index} className="flex gap-2 items-center">
                                                <select
                                                    value={line.roomTypeId}
                                                    onChange={e => updateRoomLine(index, 'roomTypeId', e.target.value)}
                                                    className="filter-input w-117"
                                                    required
                                                >
                                                    <option value="">Select a room type...</option>
                                                    {[...roomTypes].sort((a, b) => a.name.localeCompare(b.name)).map(rt => (
                                                        <option key={rt.id} value={rt.id}>{rt.name.replace('_', ' ')}</option>
                                                    ))}
                                                </select>
                                                <Stepper
                                                    value={line.quantity}
                                                    onChange={qty => updateRoomLine(index, 'quantity', qty)}
                                                    min={1}
                                                    max={roomTypes.find(rt => String(rt.id) === String(line.roomTypeId))?.availableCount ?? 1}
                                                    disabled={!line.roomTypeId}
                                                />
                                                {roomLines.length > 1 && (
                                                    <button
                                                        type="button"
                                                        onClick={() => removeRoomLine(index)}
                                                        className="p-2 text-muted hover:text-error transition-colors"
                                                        aria-label="Remove room"
                                                        title="Remove room"
                                                    >
                                                        <Trash2 size={16} />
                                                    </button>
                                                )}
                                            </div>
                                        ))}
                                        <button type="button" onClick={addRoomLine} className="btn btn-secondary text-sm mt-2">
                                            + Add another room type
                                        </button>
                                    </div>
                                ) : (
                                    <select name="roomTypeId" value={form.roomTypeId} onChange={handleChange} className="filter-input w-full sm:w-56" required>
                                        <option value="">Select a room type...</option>
                                        {[...roomTypes].sort((a, b) => a.name.localeCompare(b.name)).map(rt => (
                                            <option key={rt.id} value={rt.id} disabled={unavailableRoomTypeIds.includes(rt.id)}>
                                                {rt.name.replace('_', ' ')}{unavailableRoomTypeIds.includes(rt.id) ? ' (Unavailable)' : ''}
                                            </option>
                                        ))}
                                    </select>
                                )}
                            </div>
                        </div>
                        <div>
                            <div style={{ paddingLeft: dateLeftInset }}>
                                <label className="block text-sm text-muted mb-1">Check-in / Check-out</label>
                            </div>
                            <ReservationDatePicker
                                roomTypeId={form.roomTypeId}
                                checkInDate={form.checkInDate}
                                checkOutDate={form.checkOutDate}
                                onRangeSelected={({ checkInDate, checkOutDate }) => setForm(f => ({ ...f, checkInDate, checkOutDate }))}
                                excludeReservationId={reservation?.id}
                                onLeftInsetChange={setDateLeftInset}
                            />
                        </div>

                        {isEditing && (
                            <div className="flex items-end justify-between gap-4" style={{ paddingLeft: dateLeftInset, paddingRight: dateLeftInset }}>
                                <div>
                                    <label className="block text-sm text-muted mb-1">Status</label>
                                    <select name="status" value={form.status} onChange={handleChange} className="filter-input">
                                        <option value="CONFIRMED">Confirmed</option>
                                        <option value="CANCELLED">Cancelled</option>
                                    </select>
                                </div>
                                {canAddExtras && (
                                    <button type="button" onClick={() => setShowExtras(!showExtras)} className="btn btn-secondary !py-2 !px-3 !text-sm">
                                        {showExtras ? 'Hide Extras' : 'Add Extras'}
                                    </button>
                                )}
                            </div>
                        )}

                        {!isEditing && (
                            <div>
                                <button type="button" onClick={() => setShowExtras(!showExtras)} className="text-sm font-medium text-green hover:text-black">
                                    {showExtras ? 'Hide Extras' : 'Add Extras'}
                                </button>
                            </div>
                        )}

                        {(canAddExtras || !isEditing) && showExtras && (
                            <div className="flex flex-col gap-2">
                                <div className="flex gap-2 items-end">
                                    <select value={selectedExtraId} onChange={e => setSelectedExtraId(e.target.value)} className="filter-input flex-1">
                                        <option value="">Select an extra...</option>
                                        {extras.map(extra => (
                                            <option key={extra.id} value={extra.id}>
                                                {extra.name} (${extra.price.toFixed(2)}{extra.billingType === 'PER_NIGHT' ? '/night' : ''})
                                            </option>
                                        ))}
                                    </select>
                                    <input type="number" min="1" value={extraQuantity} onChange={e => setExtraQuantity(e.target.value)} className="filter-input w-20" />
                                    <button type="button" onClick={handleAddExtra} className="btn btn-secondary">Add</button>
                                </div>

                                {canAddExtras && extraMessage && <p className="text-sm text-muted">{extraMessage}</p>}

                                {!canAddExtras && stagedExtras.length > 0 && (
                                    <ul className="flex flex-col gap-1">
                                        {stagedExtras.map((item, i) => (
                                            <li key={i} className="flex justify-between items-center text-sm text-black">
                                                <span>
                                                    {item.name} x{item.quantity} (${item.price.toFixed(2)}{item.billingType === 'PER_NIGHT' ? '/night' : ''})
                                                </span>
                                                <button type="button" onClick={() => removeStagedExtra(i)} className="text-xs text-error hover:underline">
                                                    Remove
                                                </button>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>
                        )}

                        {flaggedMatch && (
                            <div className="bg-red-100 text-red-700 text-sm rounded p-2">
                                Warning: this guest is flagged — {flaggedMatch.flagReason}
                            </div>
                        )}
                    </div>

                    <div className="flex flex-col gap-3 px-6 py-4 border-t border-tan flex-shrink-0">
                        {estimate && <p className="text-sm text-black font-medium">Grand Total: ${estimate.total.toFixed(2)}</p>}

                        {error && <p className="text-sm text-error">{error}</p>}

                        <div className="flex justify-between gap-3">
                            <button type="button" onClick={() => { setGuestStepOrigin('form'); setStep('guestList') }} className="btn btn-secondary">
                                Change Guest
                            </button>
                            <div className="flex gap-3">
                                <button type="button" onClick={onClose} className="btn btn-secondary">
                                    Cancel
                                </button>
                                <button type="submit" className="btn btn-primary" disabled={isEditing && !isDirty}>
                                    {isEditing ? 'Save' : 'Continue'}
                                </button>
                            </div>
                        </div>
                    </div>
                </form>
            )}

            {step === 'pay-timing' && (
                <div className="flex flex-col flex-1 min-h-0 px-6 pb-6 gap-4">
                    <p className="text-sm text-muted">
                        {isFutureWalkIn
                            ? `Check-in is ${differenceInCalendarDays(parseISO(form.checkInDate), new Date())} days away — there's no guest here yet to charge.`
                            : "The guest isn't present to hand over a card right now."}
                    </p>
                    <div className="flex flex-col sm:flex-row gap-3">
                        <button
                            type="button"
                            onClick={() => setPayTimingChoice(payTimingChoice === 'now' ? null : 'now')}
                            className={`bg-warm-white rounded flex-1 flex flex-col gap-1 p-4 text-left ${payTimingChoice === 'now' ? 'border-2 border-black' : 'border-2 border-tan'}`}
                        >
                            <span className="font-semibold text-black">Pay Now</span>
                            <p className="text-sm text-muted">
                                {isFutureWalkIn
                                    ? "Charge the full stay today. Room assignment and the door code still happen when the guest actually arrives."
                                    : "Collect the guest's card over the phone now and charge the full stay today."}
                            </p>
                        </button>
                        <button
                            type="button"
                            onClick={() => setPayTimingChoice(payTimingChoice === 'later' ? null : 'later')}
                            className={`bg-warm-white rounded flex-1 flex flex-col gap-1 p-4 text-left ${payTimingChoice === 'later' ? 'border-2 border-black' : 'border-2 border-tan'}`}
                        >
                            <span className="font-semibold text-black">{isFutureWalkIn ? 'Pay at Check-In' : 'Pay Later (at Check-In)'}</span>
                            <p className="text-sm text-muted">
                                No charge now. The full stay will be charged via the card-present terminal when the guest actually arrives and checks in.
                            </p>
                        </button>
                    </div>
                    <div className="flex justify-between mt-2">
                        <button type="button" onClick={() => setStep('assign-room')} className="btn btn-secondary">
                            Back
                        </button>
                        <button
                            type="button"
                            onClick={() => payTimingChoice === 'now' ? handlePayNowChosen() : handlePayLaterChosen()}
                            className="btn btn-primary"
                            disabled={payTimingChoice == null}
                        >
                            OK
                        </button>
                    </div>
                </div>
            )}

            {step === 'payment' && (
                <div className="px-6 pb-6 overflow-y-auto">
                    <AcceptJsCardForm onCapture={handleCapture} onCancel={() => setStep('pay-timing')} submitLabel="Confirm & Reserve" amount={estimate?.total} label="Estimated Total" />
                </div>
            )}

            {step === 'pay-now' && (
                <div className="px-6 pb-6 overflow-y-auto">
                    <PaymentMethodStep
                        amount={payNowAmount}
                        amountLabel="Total Charge"
                        description="Charge the full stay for this booking now. Room assignment and the door code still happen when the guest actually arrives."
                        dual={false}
                        submitLabel="Charge"
                        onSubmitToken={async (roomToken) => {
                            await payFullStayNow(payNowReservationId, roomToken)
                            completeReservation(payNowReservation)
                        }}
                        onSubmitTerminal={async (deviceId) => {
                            await payFullStayNowTerminal(payNowReservationId, deviceId)
                            completeReservation(payNowReservation)
                        }}
                        onCancel={() => completeReservation(payNowReservation)}
                        terminalErrorMessage="Failed to charge card. It may have been declined on the terminal."
                        recordOnlyErrorMessage="Failed to charge card."
                    />
                </div>
            )}
        </Modal>
    )
}

export default ReservationModal