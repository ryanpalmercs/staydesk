import { useEffect, useRef, useState } from "react"
import { createReservation, getReservationEstimate, updateReservation } from "../api/reservationApi"
import { getRoomTypes } from "../api/roomTypeApi"
import { createGuest, getGuests, updateGuest } from "../api/guestApi"
import { formatPhone } from "../utils/phone"
import { getFolioByReservationId, addFolioItem } from "../api/folioApi"
import { getExtras } from "../api/extrasApi"
import AcceptJsCardForm from "./AcceptJsCardForm"
import { getPropertySetting } from "../api/settingsApi"
import ReservationDatePicker from "./ReservationDatePicker"
import { differenceInCalendarDays, parseISO } from "date-fns"
import { CircleMinus, CirclePlus } from "lucide-react"
import Modal from "./Modal"


function Stepper({ label, value, min, max, onChange }) {
    return (
        <div flex items-center justify-center>
            <label className="block text-sm text-muted mb-1">{label}</label>
            <div className="flex items-center justify-center gap-3">
                <button type="button stepper" onClick={() => onChange(Math.max(min, value - 1))} disabled={value <= min}
                    className="w-8 h-8 flex items-center justify-center p-0 color-tan" aria-label={`Decrease ${label}`}>
                    <CircleMinus size={18} />
                </button>
                <span className="w-6 text-center">{value}</span>
                <button type="button stepper" onClick={() => onChange(Math.min(max, value + 1))} disabled={value >= max}
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
        channel: null
    })

    const [guestForm, setGuestForm] = useState({
        firstName: '',
        lastName: '',
        email: '',
        phoneNumber: '',
        smsConsent: false
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

    const [step, setStep] = useState(isEditing ? 'form' : 'choice')
    const [guestStepOrigin, setGuestStepOrigin] = useState('choice')
    const [guestSearchQuery, setGuestSearchQuery] = useState('')
    const [editingGuestInfo, setEditingGuestInfo] = useState(false)
    const [pendingForm, setPendingForm] = useState(null)
    const [provider, setProvider] = useState(null)
    const paymentReady = provider === 'authorizenet'

    const selectedGuest = guests.find(g => g.id === Number(form.guestId))
    const flaggedMatch = selectedGuest?.flagged ? selectedGuest : null

    const newGuestFlaggedMatch = guests.find(g => g.flagged && (
        (guestForm.email && g.email.toLowerCase() === guestForm.email.toLowerCase()) ||
        (guestForm.phoneNumber && g.phoneNumber === guestForm.phoneNumber)
    ))

    const visibleGuests = [...guests]
        .filter(g => `${g.firstName} ${g.lastName}`.toLowerCase().includes(guestSearchQuery.toLowerCase()))
        .sort((a, b) => `${a.firstName} ${a.lastName}`.localeCompare(`${b.firstName} ${b.lastName}`))

    const totalNights = form.checkInDate && form.checkOutDate
        ? differenceInCalendarDays(parseISO(form.checkOutDate), parseISO(form.checkInDate))
        : 0

    const rateType = totalNights > 0 && totalNights % 7 === 0 ? 'WEEKLY_7'
        : totalNights > 0 && totalNights % 5 === 0 ? 'WEEKLY_5'
            : 'NIGHTLY'
    const maxGuestCount = 4
    const guestCount = form.adults + form.children

    const [estimate, setEstimate] = useState(null)

    useEffect(() => {
        getRoomTypes().then(res => setRoomTypes(res.data ?? [])),
            getGuests().then(res => setGuests(res.data ?? []))

        if (canAddExtras) {
            getFolioByReservationId(reservation.id).then(res => setFolioId(res.data.id))
            getExtras().then(res => setExtras(res.data ?? []))
        }

        if (!isEditing) {
            getPropertySetting('payment_provider').then(res => {
                setProvider(res.data.value)
            })
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
        getReservationEstimate({ rateType, guestCount, checkInDate: form.checkInDate, checkOutDate: form.checkOutDate })
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
    }, [rateType, guestCount, form.checkInDate, form.checkOutDate])

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
            setGuestForm({ firstName: '', lastName: '', email: '', phoneNumber: '', smsConsent: false })
            setStep('form')
        } catch (err) {
            if (err.response?.status === 400) {
                setGuestFormError('Phone number must be 10 digits.')
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
            email: selectedGuest.email,
            phoneNumber: selectedGuest.phoneNumber,
            smsConsent: selectedGuest.smsConsent
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

    async function handleSubmit(e) {
        e.preventDefault()

        setError(null)

        if (!form.guestId) {
            setError('Please select a guest.')
            return
        }

        if (!form.roomTypeId) {
            setError('Please select a room type.')
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

        const { adults, children, ...rest } = form
        const submittedForm = { ...rest, rateType, guestCount }

        try {
            if (isEditing) {
                await updateReservation(reservation.id, { ...reservation, ...submittedForm })
            } else {
                if (form.channel === 'WALK_IN') {
                    try {
                        const res = await createReservation({ ...submittedForm, roomPaymentMethodId: null })
                        onSaved(res.data.id)
                    } catch (err) {
                        setError(err.response?.status === 400 ? 'No room of this type is available for the selected dates.' : 'Something went wrong.')
                    }

                    return
                }

                if (!paymentReady) {
                    setError('Payment provider is not connected. Check Settings.')
                    return
                }
                setPendingForm(submittedForm)
                setStep('payment')
                return
            }

            onSaved()
        } catch (err) {
            if (err.response?.status === 400) {
                setError('No room of this type is available for the selected dates.')
            } else if (err.response?.status === 404) {
                setError('Room type not found.')
            } else {
                setError('Something went wrong.')
            }
        }
    }

    async function handleCapture(paymentMethodId) {
        try {
            await createReservation({ ...pendingForm, roomPaymentMethodId: paymentMethodId })
            onSaved()
        } catch (err) {
            setStep('form')
            if (err.response?.status === 400) {
                setError('No room of this type is available for the selected dates.')
            } else {
                setError('Something went wrong.')
            }
        }

    }

    return (
        <Modal onClose={onClose} size="reservation" scrollable padded={false} isDirty={isDirty}>
            <h2 className="text-lg text-black font-semibold px-6 pt-6 pb-4">
                {step === 'payment' ? 'Card Details'
                    : step === 'choice' ? 'New or Returning Guest?'
                        : step === 'guestList' ? 'Select Guest'
                            : step === 'newGuest' ? 'New Guest'
                                : step === 'confirmGuest' ? 'Confirm Guest Information'
                                    : isEditing ? `Edit Reservation for ${selectedGuest ? `${selectedGuest.firstName} ${selectedGuest.lastName}` : ''}` : `New Reservation for ${selectedGuest ? `${selectedGuest.firstName} ${selectedGuest.lastName}` : ''}`}
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
                                <span>{g.firstName} {g.lastName}</span>
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
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                            <input name="firstName" placeholder="First name" value={guestForm.firstName} onChange={handleGuestFieldChange} className="filter-input" required />
                            <input name="lastName" placeholder="Last name" value={guestForm.lastName} onChange={handleGuestFieldChange} className="filter-input" required />
                            <input name="email" placeholder="Email" value={guestForm.email} onChange={handleGuestFieldChange} className="filter-input" required />
                            <input name="phoneNumber" placeholder="Phone (10 digits)" value={guestForm.phoneNumber} onChange={handleGuestFieldChange} className="filter-input" required />
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
                                <p className="text-sm text-black">{selectedGuest.firstName} {selectedGuest.lastName}</p>
                            </div>
                            <div>
                                <label className="block text-sm text-muted mb-1">Email</label>
                                <p className="text-sm text-black">{selectedGuest.email}</p>
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
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                                <input name="firstName" placeholder="First name" value={guestForm.firstName} onChange={handleGuestFieldChange} className="filter-input" required />
                                <input name="lastName" placeholder="Last name" value={guestForm.lastName} onChange={handleGuestFieldChange} className="filter-input" required />
                                <input name="email" placeholder="Email" value={guestForm.email} onChange={handleGuestFieldChange} className="filter-input" required />
                                <input name="phoneNumber" placeholder="Phone (10 digits)" value={guestForm.phoneNumber} onChange={handleGuestFieldChange} className="filter-input" required />
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
                    <div className="flex flex-col gap-4 overflow-y-auto px-6 flex-1 min-h-0">
                        <div className="flex flex-col sm:flex-row sm:justify-center gap-4 sm:gap-10">
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
                                <label className="block text-sm text-muted mb-1">Room Type</label>
                                <select name="roomTypeId" value={form.roomTypeId} onChange={handleChange} className="filter-input" required>
                                    <option value="">Select a room type...</option>
                                    {[...roomTypes].sort((a, b) => a.name.localeCompare(b.name)).map(rt => (
                                        <option key={rt.id} value={rt.id}>{rt.name.replace('_', ' ')}</option>
                                    ))}
                                </select>
                            </div>
                        </div>
                        <div>
                            <label className="block text-sm text-muted mb-1">Check-in / Check-out</label>
                            <ReservationDatePicker
                                roomTypeId={form.roomTypeId}
                                checkInDate={form.checkInDate}
                                checkOutDate={form.checkOutDate}
                                onRangeSelected={({ checkInDate, checkOutDate }) => setForm(f => ({ ...f, checkInDate, checkOutDate }))}
                            />
                        </div>

                        {isEditing && (
                            <div>
                                <label className="block text-sm text-muted mb-1">Status</label>
                                <div className="flex justify-center">
                                    <select name="status" value={form.status} onChange={handleChange} className="filter-input" >
                                        <option value="CONFIRMED">Confirmed</option>
                                        <option value="CANCELLED">Cancelled</option>
                                    </select>
                                </div>
                            </div>
                        )}

                        {canAddExtras && (
                            <div>
                                <button type="button" onClick={() => setShowExtras(!showExtras)} className="text-sm font-medium text-green hover:text-black">
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
                                    {isEditing ? 'Save' : 'Create'}
                                </button>
                            </div>
                        </div>
                    </div>
                </form>
            )}

            {step === 'payment' && (
                <div className="px-6 pb-6 overflow-y-auto">
                    <AcceptJsCardForm onCapture={handleCapture} onCancel={() => setStep('form')} submitLabel="Confirm & Reserve" amount={estimate?.total} label="Estimated Total" />
                </div>
            )}

        </Modal>
    )
}

export default ReservationModal