import { useEffect, useMemo, useRef, useState } from "react"
import { useCombobox } from "downshift"
import { createReservation, updateReservation } from "../api/reservationApi"
import { getRooms } from "../api/roomApi"
import { createGuest, getGuests } from "../api/guestApi"
import { getRates } from "../api/rateApi"
import { getFolioByReservationId, addFolioItem } from "../api/folioApi"
import { getExtras } from "../api/extrasApi"
import { getConnectStatus } from "../api/stripeApi"
import { loadStripe } from "@stripe/stripe-js"
import { CardElement, Elements, useElements, useStripe } from "@stripe/react-stripe-js"


function CardCaptureForm({ onCapture, onCancel }) {
    const stripe = useStripe()
    const elements = useElements()
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState(null)

    async function handleSubmit(e) {
        e.preventDefault()
        if (!stripe || !elements) return
        setSubmitting(true)
        setError(null)
        const card = elements.getElement(CardElement)
        const result = await stripe.createPaymentMethod({ type: 'card', card })
        if (result.error) {
            setError(result.error.message)
            setSubmitting(false)
            return
        }
        try {
            await onCapture(result.paymentMethod.id)
        } catch {
            setError('Failed to create reservation. Please try a different card.')
            setSubmitting(false)
        }
    }

    return (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="filter-input">
                <CardElement options={{ style: { base: { fontSize: '16px' } } }} />
            </div>
            {error && <p className="text-sm text-rust">{error}</p>}
            <div className="flex justify-end gap-3 mt-2">
                <button type="button" onClick={onCancel} className="btn btn-secondary" disabled={submitting}>Back</button>
                <button type="submit" className="btn btn-primary" disabled={!stripe || submitting}>
                    {submitting ? 'Reserving...' : 'Confirm & Reserve'}
                </button>
            </div>
        </form>
    )
}

function CardCaptureStep({ stripePromise, onCapture, onCancel }) {
    return (
        <Elements stripe={stripePromise}>
            <CardCaptureForm onCapture={onCapture} onCancel={onCancel} />
        </Elements>
    )
}

function SearchableSelect({ items, selectedId, itemKey = 'id', itemLabel, renderBadge, onSelect, placeholder }) {
    const sortedItems = useMemo(
        () => [...items].sort((a, b) => itemLabel(a).localeCompare(itemLabel(b))),
        [items]
    )
    const [inputItems, setInputItems] = useState(sortedItems)
    const selectedItem = items.find(i => i[itemKey] === selectedId) ?? null

    useEffect(() => {
        setInputItems(sortedItems)
    }, [sortedItems])

    const { isOpen, getMenuProps, getInputProps, getItemProps, highlightedIndex } = useCombobox({
        items: inputItems,
        itemToString: item => (item ? itemLabel(item) : ''),
        selectedItem,
        onInputValueChange: ({ inputValue }) => {
            setInputItems(
                sortedItems.filter(i => itemLabel(i).toLowerCase().includes((inputValue ?? '').toLowerCase()))
            )
        },
        onSelectedItemChange: ({ selectedItem }) => {
            onSelect(selectedItem ? selectedItem[itemKey] : '')
        },
    })

    return (
        <div className="relative">
            <input {...getInputProps({ placeholder })} className="filter-input w-full" />
            <ul {...getMenuProps()} className={`absolute z-10 w-full bg-warm-white border border-tan rounded-md mt-1 max-h-48 overflow-auto shadow-lg ${isOpen ? '' : 'hidden'}`}>
                {isOpen &&
                    inputItems.map((item, index) => (
                        <li
                            key={item[itemKey]}
                            {...getItemProps({ item, index })}
                            className={`px-3 py-2 text-sm cursor-pointer flex justify-between ${highlightedIndex === index ? 'bg-tan' : ''}`}
                        >
                            <span>{itemLabel(item)}</span>
                            {renderBadge?.(item)}
                        </li>
                    ))}
            </ul>
        </div>
    )
}

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

    const [step, setStep] = useState('form')
    const [pendingForm, setPendingForm] = useState(null)
    const [stripePromise, setStripePromise] = useState(null)

    const selectedRate = rates.find(r => r.rateType === form.rateType && r.guestCount === Number(form.guestCount))

    const selectedGuest = guests.find(g => g.id === Number(form.guestId))
    const flaggedMatch = guestMode === 'search'
        ? (selectedGuest?.flagged ? selectedGuest : null)
        : guests.find(g => g.flagged && (
            (guestForm.email && g.email.toLowerCase() === guestForm.email.toLowerCase()) ||
            (guestForm.phoneNumber && g.phoneNumber === guestForm.phoneNumber)
        ))

    useEffect(() => {
        getRooms().then(res => setRooms(res.data ?? [])),
            getGuests().then(res => setGuests(res.data ?? [])),
            getRates().then(res => setRates(res.data ?? []))

        if (canAddExtras) {
            getFolioByReservationId(reservation.id).then(res => setFolioId(res.data.id))
            getExtras().then(res => setExtras(res.data ?? []))
        }

        if (!isEditing) {
            getConnectStatus().then(res => {
                if (res.data.connected) {
                    setStripePromise(loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY, { stripeAccount: res.data.accountId }))
                }
            })
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

        if (guestMode === 'search' && !form.guestId) {
            setError('Please select a guest.')
            return
        }

        if (!form.roomId) {
            setError('Please select a room.')
            return
        }

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
                console.debug(submittedForm)
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
                if (!stripePromise) {
                    setError('Stripe is not connected. Connect an account in Settings first.')
                    return
                }
                setPendingForm(submittedForm)
                setStep('payment')
                return
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

    async function handleCapture(paymentMethodId) {
        try {
            await createReservation({ ...pendingForm, roomPaymentMethodId: paymentMethodId })
            onSaved()
        } catch (err) {
            setStep('form')
            if (err.response?.status === 400) {
                setError('Room is unavailable or dates conflict with an existing reservation.')
            } else {
                setError('Something went wrong.')
            }
        }

    }

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-warm-white rounded-lg p-6 w-full max-w-md shadow-lg border-t-4 border-rust">
                <h2 className="text-lg text-charcoal font-semibold mb-4">
                    {step === 'payment' ? 'Card Details' : isEditing ? 'Edit Reservation' : 'New Reservation'}
                </h2>
                {step === 'form' && (
                    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                        <div>
                            <div className="flex items-baseline gap-2">
                                <label className="text-sm text-muted">Guest</label>
                                <button type="button" onClick={onGuestModeChange} className="text-sm font-medium text-rust hover:text-rust-light">
                                    {guestMode === 'search' ? 'New Guest' : 'Select Existing'}
                                </button>
                            </div>
                            {guestMode === 'search' ? (
                                <SearchableSelect
                                    items={guests}
                                    selectedId={form.guestId}
                                    itemLabel={g => `${g.firstName} ${g.lastName}`}
                                    renderBadge={g => g.flagged && <span className="text-xs text-rust font-medium">Flagged</span>}
                                    onSelect={guestId => setForm({ ...form, guestId })}
                                    placeholder="Search guests..."
                                />
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
                            <SearchableSelect
                                items={rooms}
                                selectedId={form.roomId}
                                itemLabel={r => `Room ${r.roomNumber}`}
                                onSelect={roomId => setForm({ ...form, roomId })}
                                placeholder="Search rooms..."
                            />
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

                        {flaggedMatch && (
                            <div className="bg-red-100 text-red-700 text-sm rounded p-2">
                                Warning: this guest is flagged — {flaggedMatch.flagReason}
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
                )}

                {step === 'payment' && (
                    <CardCaptureStep
                        stripePromise={stripePromise}
                        onCapture={handleCapture}
                        onCancel={() => setStep('form')}
                    />
                )}
            </div>
        </div>
    )
}

export default ReservationModal