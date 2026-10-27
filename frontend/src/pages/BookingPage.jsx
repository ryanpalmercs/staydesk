import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { DateRange } from 'react-date-range'
import 'react-date-range/dist/styles.css'
import 'react-date-range/dist/theme/default.css'
import '../components/ReservationDatePicker.css'
import AcceptJsCardForm from '../components/AcceptJsCardForm'
import { displayPrice } from '../utils/price'
import { toIsoDate, parseIsoDate, todayIso, addDaysIso, datesInRange } from '../utils/date'
import { useIsDesktop } from '../hooks/useIsDesktop'
import { useOutsideClick } from '../hooks/useOutsideClick'
import { useEscapeKey } from '../hooks/useEscapeKey'
import { X } from 'lucide-react';
import { getRoomTypes, getRates, getOccupiedDates, getAddOns, getEstimate, createBooking } from '../api/publicBookingApi'

const STEP_ORDER = ['room', 'addons', 'guestPayment', 'confirmation']
const STEP_LABELS = {
    room: 'Select a Room', addons: 'Customize Your Stay', guestPayment: 'Payment and Guest Details',
    confirmation: 'Confirmation'
}
const RATE_TYPE = 'NIGHTLY'
const MAX_GUEST_COUNT = 4

// PER_NIGHT add-ons (e.g. the pet fee, a rollaway cot) charge once per night of the stay, same as
// FolioService.priceExtras on the backend -- this has to mirror that multiplication exactly or
// the price shown here during selection/review won't match what's actually charged at booking.
function addOnTotal(addOn, nights) {
    return parseFloat(addOn.price) * (addOn.billingType === 'PER_NIGHT' ? nights : 1)
}

function StepProgress({ step }) {
    const index = STEP_ORDER.indexOf(step)
    return (
        <div className="mb-6">
            <p className="text-xs font-semibold text-muted uppercase tracking-wide mb-2">Step {index + 1} of {STEP_ORDER.length}</p>
            <div className="h-1 bg-tan rounded-full mb-4 overflow-hidden">
                <div className="h-full bg-green transition-all" style={{ width: `${((index + 1) / STEP_ORDER.length) * 100}%` }} />
            </div>
            <h1 className="section-title !mb-0">{STEP_LABELS[step]}</h1>
        </div>
    )
}

function formatDisplayDate(iso) {
    return parseIsoDate(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function formatStayRange(checkInDate, checkOutDate) {
    const start = parseIsoDate(checkInDate)
    const end = parseIsoDate(checkOutDate)
    const startStr = start.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })
    const endStr = end.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })
    return `${startStr} – ${endStr}, ${end.getFullYear()}`
}

function DateBadge({ iso, onClick }) {
    const d = parseIsoDate(iso)

    return (
        <button type="button" onClick={onClick} className="flex items-center gap-2 px-3 py-1 rounded hover:bg-tan/40">
            <span className="text-2xl font-semibold leading-none" style={{ fontFamily: "'DM Serif Display', serif" }}>{d.getDate()}</span>
            <span className="flex flex-col text-xs font-semibold text-muted leading-tight">
                <span>{d.toLocaleDateString('en-US', { month: 'short' }).toUpperCase()}</span>
                <span>{d.toLocaleDateString('en-US', { weekday: 'short' }).toUpperCase()}</span>
            </span>
        </button>
    )
}

/**
 * Persistent bar shown across every step with dates and guests as two independent fields, each
 * opening its own popover -- mirroring the homepage's BookingSearchWidget.jsx pattern rather than
 * one combined form. Dates are staged as a local draft and only committed via onDatesChange when
 * "Done" is clicked, so the caller can intercept the change (e.g. to warn about resetting a
 * room/add-on selection) before it takes effect; guest-count changes apply immediately since they
 * don't invalidate anything already selected.
 *
 * The dates/guests fields only exist in the expanded row (only ever shown on the room step, since
 * that's where "Edit stay" -- always visible, from any step -- takes you). The expanded row has
 * its own close (X), independent of which step you're on, matching Hilton: closing it just
 * collapses back to the summary line without leaving step 1.
 */
function StayBar({ checkInDate, checkOutDate, adults, childGuests, onDatesChange, onAdultsChange, onChildGuestsChange,
                   expanded, onEditStay, onCloseExpanded }) {
    const [openPopover, setOpenPopover] = useState(null) // null | 'dates' | 'guests'
    const [draftCheckIn, setDraftCheckIn] = useState(checkInDate)
    const [draftCheckOut, setDraftCheckOut] = useState(checkOutDate)
    const isDesktop = useIsDesktop()

    const datesRef = useRef(null)
    const guestsRef = useRef(null)

    useOutsideClick(datesRef, () => setOpenPopover(p => (p === 'dates' ? null : p)))
    useOutsideClick(guestsRef, () => setOpenPopover(p => (p === 'guests' ? null : p)))
    useEscapeKey(() => setOpenPopover(null))

    function openDatesPopover() {
        setDraftCheckIn(checkInDate)
        setDraftCheckOut(checkOutDate)
        setOpenPopover(p => (p === 'dates' ? null : 'dates'))
    }

    function applyDates() {
        onDatesChange(draftCheckIn, draftCheckOut)
        setOpenPopover(null)
    }

    const guestCount = adults + childGuests
    const nights = datesInRange(checkInDate, checkOutDate).length

    return (
        <div className="bg-warm-white border-b border-tan px-6 py-3">
            <div className="max-w-5xl mx-auto flex flex-wrap items-center justify-center gap-x-3 gap-y-1 text-sm">
                <span className="font-semibold flex-shrink-0">Your stay</span>
                <span className="text-black/80">{formatStayRange(checkInDate, checkOutDate)} ({nights} night{nights === 1 ? '' : 's'})</span>
                <span className="text-black/80">{guestCount} guest{guestCount > 1 ? 's' : ''}</span>
                <button type="button" onClick={onEditStay} className="text-green underline font-medium flex-shrink-0">
                    Edit stay
                </button>
            </div>

            {expanded && (
                <div className="max-w-5xl mx-auto flex flex-wrap items-center justify-center gap-2 pt-3 mt-3 border-t border-tan">
                    <span className="font-semibold text-sm flex-shrink-0">Edit stay</span>

                    <div ref={datesRef} className="relative flex items-stretch">
                        <DateBadge iso={checkInDate} onClick={openDatesPopover} />
                        <DateBadge iso={checkOutDate} onClick={openDatesPopover} />

                        {openPopover === 'dates' && (
                            <div className="absolute z-30 top-full left-0 mt-2 bg-cream rounded-lg shadow-2xl border border-tan overflow-hidden">
                                <DateRange
                                    ranges={[{ startDate: parseIsoDate(draftCheckIn), endDate: parseIsoDate(draftCheckOut), key: 'selection' }]}
                                    onChange={item => {
                                        const { startDate, endDate } = item.selection
                                        setDraftCheckIn(toIsoDate(startDate))
                                        setDraftCheckOut(startDate.getTime() === endDate.getTime() ? addDaysIso(toIsoDate(startDate), 1) : toIsoDate(endDate))
                                    }}
                                    months={isDesktop ? 2 : 1}
                                    direction={isDesktop ? 'horizontal' : 'vertical'}
                                    minDate={new Date()}
                                    showMonthAndYearPickers={false}
                                    showDateDisplay={false}
                                    monthDisplayFormat="MMMM yyyy"
                                    rangeColors={['#334428']}
                                />
                                <div className="p-3 border-t border-tan flex justify-end">
                                    <button type="button" onClick={applyDates} className="btn-primary !w-auto !px-6 !py-2 text-sm">Done</button>
                                </div>
                            </div>
                        )}
                    </div>

                    <div ref={guestsRef} className="relative">
                        <button type="button" onClick={() => setOpenPopover(p => (p === 'guests' ? null : 'guests'))}
                                className="text-sm font-medium border border-green rounded px-3 py-2 hover:bg-tan/40">
                            {guestCount} guest{guestCount > 1 ? 's' : ''}
                        </button>

                        {openPopover === 'guests' && (
                            <div className="absolute z-30 top-full right-0 mt-2 w-72 bg-cream rounded-lg shadow-2xl border border-tan p-4 flex flex-col gap-4">
                                <div className="flex items-center justify-between">
                                    <span className="text-sm font-semibold">Adults</span>
                                    <div className="flex items-center gap-3">
                                        <button type="button" onClick={() => onAdultsChange(Math.max(1, adults - 1))} disabled={adults <= 1}
                                                className="w-9 h-9 rounded-full border border-tan bg-white flex items-center justify-center disabled:opacity-40" aria-label="Decrease adults">−</button>
                                        <span className="font-semibold w-4 text-center">{adults}</span>
                                        <button type="button" onClick={() => onAdultsChange(Math.min(MAX_GUEST_COUNT - childGuests, adults + 1))} disabled={guestCount >= MAX_GUEST_COUNT}
                                                className="w-9 h-9 rounded-full border border-tan bg-white flex items-center justify-center disabled:opacity-40" aria-label="Increase adults">+</button>
                                    </div>
                                </div>
                                <div className="flex items-center justify-between">
                                    <span className="text-sm font-semibold">Children</span>
                                    <div className="flex items-center gap-3">
                                        <button type="button" onClick={() => onChildGuestsChange(Math.max(0, childGuests - 1))} disabled={childGuests <= 0}
                                                className="w-9 h-9 rounded-full border border-tan bg-white flex items-center justify-center disabled:opacity-40" aria-label="Decrease children">−</button>
                                        <span className="font-semibold w-4 text-center">{childGuests}</span>
                                        <button type="button" onClick={() => onChildGuestsChange(Math.min(MAX_GUEST_COUNT - adults, childGuests + 1))} disabled={guestCount >= MAX_GUEST_COUNT}
                                                className="w-9 h-9 rounded-full border border-tan bg-white flex items-center justify-center disabled:opacity-40" aria-label="Increase children">+</button>
                                    </div>
                                </div>
                                <button type="button" onClick={() => setOpenPopover(null)} className="btn-primary !w-auto self-end !px-6 !py-2 text-sm">Done</button>
                            </div>
                        )}
                    </div>

                    <span className="text-sm text-black/60">{nights} night{nights === 1 ? '' : 's'}</span>
                    <button type="button" onClick={onCloseExpanded} aria-label="Close" className="text-muted hover:text-black flex-shrink-0">
                            <X size={32} />
                    </button>
                </div>
            )}
        </div>
    )
}

function TotalForStayCard({ selectedRoomType, nights, nightlyRate, selectedAddOns, estimate }) {
    const [showDetails, setShowDetails] = useState(false)

    if (!estimate) return null

    const roomSubtotal = nightlyRate ? parseFloat(nightlyRate.amount) * nights : null
    const addOnsSubtotal = selectedAddOns.reduce((sum, a) => sum + addOnTotal(a, nights), 0)

    return (
        <div className="border-b border-tan pb-4 mb-6">
            <div className="flex justify-between items-baseline">
                <h2 className="text-xl font-semibold" style={{ fontFamily: "'DM Serif Display', serif" }}>Total for stay</h2>
                <span className="text-xl font-semibold">{displayPrice(estimate.total)}</span>
            </div>
            <div className="text-sm text-black/80 mt-2 flex flex-col gap-0.5">
                <div className="flex justify-between"><span>Total room charge</span><span>{displayPrice(roomSubtotal)}</span></div>
                {selectedAddOns.length > 0 && (
                    <div className="flex justify-between"><span>Total add-ons</span><span>{displayPrice(addOnsSubtotal)}</span></div>
                )}
                <div className="flex justify-between"><span>Total taxes and fees</span><span>{displayPrice(estimate.tax)}</span></div>
            </div>
            <button type="button" onClick={() => setShowDetails(d => !d)} className="text-green underline text-sm mt-3">
                {showDetails ? 'Hide price details' : 'Show price details'}
            </button>
            {showDetails && (
                <div className="text-sm text-black/80 mt-3 flex flex-col gap-2">
                    {selectedRoomType && (
                        <div className="flex flex-col">
                            <div className="flex justify-between"><span>{selectedRoomType.name} &middot; {nights} night{nights === 1 ? '' : 's'}</span>
                                {roomSubtotal != null && <span>{displayPrice(roomSubtotal)}</span>}
                            </div>
                        </div>
                    )}
                    {selectedAddOns.map(addOn => (
                        <div key={addOn.id} className="flex justify-between">
                            <span>{addOn.name}{addOn.billingType === 'PER_NIGHT' ? ` × ${nights} night${nights === 1 ? '' : 's'}` : ''}</span>
                            <span>{displayPrice(addOnTotal(addOn, nights))}</span>
                        </div>
                    ))}
                    <div className="flex justify-between font-medium border-t border-tan pt-2"><span>Taxes and government charges</span><span>{displayPrice(estimate.tax)}</span></div>
                </div>
            )}
        </div>
    )
}

function ReservationSummary({ checkInDate, checkOutDate, nights, guestCount, selectedRoomType,
                              nightlyRate, selectedAddOns, estimate, onChangeRoom, showChangeRoom }) {
    const roomSubtotal = nightlyRate ? parseFloat(nightlyRate.amount) * nights : null
    const addOnsSubtotal = selectedAddOns.reduce((sum, a) => sum + addOnTotal(a, nights), 0)
    const clientSubtotal = roomSubtotal != null ? roomSubtotal + addOnsSubtotal : null

    return (
        <aside className="bg-warm-white border border-tan rounded-lg p-5 flex flex-col gap-4 h-fit lg:sticky lg:top-6">
            <h2 className="font-semibold" style={{ fontFamily: "'DM Serif Display', serif" }}>Reservation summary</h2>

            <div className="text-sm flex flex-col gap-1">
                <div className="flex justify-between text-black/80">
                    <span>{checkInDate} &rarr; {checkOutDate}</span>
                    <span>{nights} night{nights === 1 ? '' : 's'}</span>
                </div>
                <div className="flex justify-between text-black/80">
                    <span>Guests</span>
                    <span>{guestCount}</span>
                </div>
            </div>

            {selectedRoomType && (
                <div className="text-sm border-t border-tan pt-3 flex flex-col gap-1">
                    <div className="flex justify-between">
                        <span className="font-medium">{selectedRoomType.name}</span>
                        {roomSubtotal != null && <span>{displayPrice(roomSubtotal)}</span>}
                    </div>
                    {showChangeRoom && (
                        <button type="button" onClick={onChangeRoom} className="text-green underline text-xs self-start">Change room</button>
                    )}
                </div>
            )}

            {selectedAddOns.length > 0 && (
                <div className="text-sm border-t border-tan pt-3 flex flex-col gap-1">
                    {selectedAddOns.map(addOn => (
                        <div key={addOn.id} className="flex justify-between text-black/80">
                            <span>{addOn.name}{addOn.billingType === 'PER_NIGHT' ? ` × ${nights}` : ''}</span>
                            <span>{displayPrice(addOnTotal(addOn, nights))}</span>
                        </div>
                    ))}
                </div>
            )}

            <div className="border-t border-tan pt-3 text-sm">
                {estimate ? (
                    <div className="flex flex-col gap-1">
                        <div className="flex justify-between text-black/80"><span>Subtotal</span><span>{displayPrice(estimate.subtotal)}</span></div>
                        <div className="flex justify-between text-black/80"><span>Taxes &amp; fees</span><span>{displayPrice(estimate.tax)}</span></div>
                        <div className="flex justify-between font-semibold text-base pt-1"><span>Total</span><span>{displayPrice(estimate.total)}</span></div>
                    </div>
                ) : clientSubtotal != null ? (
                    <div className="flex justify-between font-semibold">
                        <span>Estimated total</span>
                        <span>{displayPrice(clientSubtotal)} <span className="text-xs font-normal text-muted">+ tax</span></span>
                    </div>
                ) : (
                    <p className="text-muted text-xs">Select a room to see pricing.</p>
                )}
            </div>
        </aside>
    )
}

export default function BookingPage() {
    const [searchParams] = useSearchParams()

    const [step, setStep] = useState('room')
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)
    const [showResetWarning, setShowResetWarning] = useState(false)
    const [pendingDates, setPendingDates] = useState(null)
    const [stayEditorOpen, setStayEditorOpen] = useState(true)

    const [checkInDate, setCheckInDate] = useState(searchParams.get('checkIn') || todayIso())
    const [checkOutDate, setCheckOutDate] = useState(searchParams.get('checkOut') || addDaysIso(todayIso(), 1))
    const [adults, setAdults] = useState(Number(searchParams.get('adults')) || 2)
    const [children, setChildren] = useState(Number(searchParams.get('children')) || 0)
    const guestCount = adults + children

    const [roomTypes, setRoomTypes] = useState([])
    const [rates, setRates] = useState([])
    const [occupiedDatesByRoomType, setOccupiedDatesByRoomType] = useState({})
    const [selectedRoomTypeId, setSelectedRoomTypeId] = useState(null)

    const [addOns, setAddOns] = useState([])
    const [selectedAddOnIds, setSelectedAddOnIds] = useState([])
    const [specialRequests, setSpecialRequests] = useState('')

    const [firstName, setFirstName] = useState('')
    const [lastName, setLastName] = useState('')
    const [email, setEmail] = useState('')
    const [phoneNumber, setPhoneNumber] = useState('')
    const [smsConsent, setSmsConsent] = useState(true)
    const [website, setWebsite] = useState('')

    const [billingStreet, setBillingStreet] = useState('')
    const [billingStreet2, setBillingStreet2] = useState('')
    const [billingCity, setBillingCity] = useState('')
    const [billingState, setBillingState] = useState('')
    const [billingZip, setBillingZip] = useState('')

    const [estimate, setEstimate] = useState(null)
    const [confirmation, setConfirmation] = useState(null)

    useEffect(() => {
        getRoomTypes().then(res => {
            setRoomTypes(res.data)
            Promise.all(res.data.map(rt => getOccupiedDates(rt.id).then(occ => [rt.id, occ.data])))
                .then(results => setOccupiedDatesByRoomType(Object.fromEntries(results)))
                .catch(() => setError('Could not load availability. Please try again.'))
        }).catch(() => setError('Could not load room types. Please try again.'))
        getRates().then(res => setRates(res.data)).catch(() => setError('Could not load rates. Please try again.'))
    }, [])

    const nightlyRate = rates.find(r => r.rateType === RATE_TYPE && r.guestCount === guestCount)
    const selectedRoomType = roomTypes.find(rt => rt.id === selectedRoomTypeId)
    const selectedAddOns = addOns.filter(a => selectedAddOnIds.includes(a.id))
    const nights = datesInRange(checkInDate, checkOutDate).length

    // PLACEHOLDER: 24-hour window is provisional, same as the [PLACEHOLDER] cancellation section in
    // TermsOfServicePage.jsx -- correct both once Martin House confirms the real policy.
    const cancellationDeadline = parseIsoDate(addDaysIso(checkInDate, -1))
        .toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })

    const requestedDates = datesInRange(checkInDate, checkOutDate)
    const unavailableRoomTypeIds = Object.entries(occupiedDatesByRoomType)
        .filter(([, occupiedDates]) => requestedDates.some(d => occupiedDates.includes(d)))
        .map(([id]) => Number(id))

    function requestDatesChange(newCheckIn, newCheckOut) {
        if (newCheckIn === checkInDate && newCheckOut === checkOutDate) {
            return
        }

        // Only warn if there's actually a room/add-on selection at risk of being reset.
        if (selectedRoomTypeId) {
            setPendingDates({ checkInDate: newCheckIn, checkOutDate: newCheckOut })
            setShowResetWarning(true)
            return
        }

        setCheckInDate(newCheckIn)
        setCheckOutDate(newCheckOut)
    }

    function confirmStayEdit() {
        if (pendingDates) {
            setCheckInDate(pendingDates.checkInDate)
            setCheckOutDate(pendingDates.checkOutDate)
        }

        setSelectedRoomTypeId(null)
        setSelectedAddOnIds([])
        setShowResetWarning(false)
        setPendingDates(null)
    }

    function cancelResetWarning() {
        setShowResetWarning(false)
        setPendingDates(null)
    }

    function toggleAddOn(id) {
        setSelectedAddOnIds(prev => prev.includes(id) ? prev.filter(a => a !== id) : [...prev, id])
    }

    async function goToAddOnsStep(e) {
        e.preventDefault()

        if (!selectedRoomTypeId) {
            setError('Please select a room.')
            return
        }

        setError(null)
        setSubmitting(true)

        try {
            const res = await getAddOns(selectedRoomTypeId)
            setAddOns(res.data)
            setSelectedAddOnIds(prev => prev.filter(id => res.data.some(a => a.id === id)))
            setStep('addons')
        } catch {
            setError('Could not load add-ons. Please try again.')
        } finally {
            setSubmitting(false)
        }
    }

    async function goToGuestPaymentStep(e) {
        e.preventDefault()
        setError(null)
        setSubmitting(true)

        try {
            const res = await getEstimate(RATE_TYPE, guestCount, checkInDate, checkOutDate, selectedRoomTypeId, selectedAddOnIds)
            setEstimate(res.data)
            setStep('guestPayment')
        } catch {
            setError('Could not calculate the total for your stay. Please try again.')
        } finally {
            setSubmitting(false)
        }
    }

    async function handlePayment(paymentToken) {
        try {
            const res = await createBooking({
                roomTypeId: selectedRoomTypeId,
                checkInDate,
                checkOutDate,
                rateType: RATE_TYPE,
                guestCount,
                firstName,
                lastName,
                email,
                phoneNumber,
                smsConsent,
                paymentToken,
                website,
                addOnIds: selectedAddOnIds,
                specialRequests,
                billingStreet,
                billingStreet2,
                billingCity,
                billingState,
                billingZip
            })
            setConfirmation(res.data)
            setStep('confirmation')
        } catch (err) {
            const message = typeof err.response?.data === 'string'
                ? err.response.data
                : 'We could not complete your booking. Please try again or call the front desk at (660) 258-7257.'
            throw new Error(message)
        }
    }

    const summary = (
        <ReservationSummary
            checkInDate={checkInDate} checkOutDate={checkOutDate} nights={nights} guestCount={guestCount}
            selectedRoomType={selectedRoomType} nightlyRate={nightlyRate} selectedAddOns={selectedAddOns} estimate={estimate}
            onChangeRoom={() => { setError(null); setStep('room') }} showChangeRoom={step !== 'room'}
        />
    )

    return (
        <div className="min-h-screen bg-cream">
            <div className="bg-black px-6 py-4 flex items-center justify-center">
                <a href="/" className="text-cream" style={{ fontFamily: "'DM Serif Display', serif" }}>Martin House Motel</a>
            </div>

            {step !== 'confirmation' && (
                <StayBar checkInDate={checkInDate} checkOutDate={checkOutDate} adults={adults} childGuests={children}
                         onDatesChange={requestDatesChange} onAdultsChange={setAdults} onChildGuestsChange={setChildren}
                         expanded={step === 'room' && stayEditorOpen}
                         onEditStay={() => { setError(null); setStep('room'); setStayEditorOpen(true) }}
                         onCloseExpanded={() => setStayEditorOpen(false)} />
            )}

            <div className="max-w-5xl mx-auto px-6 py-8">
                {step !== 'confirmation' ? (
                    <div className="grid lg:grid-cols-[1fr_300px] gap-8 items-start">
                        <div>
                            {step === 'room' && (
                                <>
                                    <StepProgress step={step} />
                                    {error && <p className="text-error text-sm mb-4">{error}</p>}
                                    <form onSubmit={goToAddOnsStep}>
                                        <div className="grid sm:grid-cols-2 gap-4 mb-6">
                                            {roomTypes.map(rt => {
                                                const unavailable = unavailableRoomTypeIds.includes(rt.id)
                                                const selected = selectedRoomTypeId === rt.id
                                                return (
                                                    <button type="button" key={rt.id} disabled={unavailable}
                                                            onClick={() => setSelectedRoomTypeId(rt.id)}
                                                            className={`text-left rounded-lg p-4 border bg-warm-white ${selected ? 'border-2 border-green' : 'border-tan'} ${unavailable ? 'opacity-50 cursor-not-allowed' : ''}`}>
                                                        <div className="flex items-start justify-between gap-2">
                                                            <span className="font-semibold" style={{ fontFamily: "'DM Serif Display', serif" }}>{rt.name}</span>
                                                            {unavailable && <span className="text-xs text-error flex-shrink-0">Not available</span>}
                                                        </div>
                                                        <p className="text-sm text-muted mt-1">Sleeps up to {MAX_GUEST_COUNT} guests{rt.petFriendly ? ' · Pet friendly' : ''}</p>
                                                        {nightlyRate && !unavailable && (
                                                            <p className="text-sm font-medium mt-2">{displayPrice(nightlyRate.amount)} / night</p>
                                                        )}
                                                    </button>
                                                )
                                            })}
                                        </div>
                                        <button type="submit" className="btn-primary sm:w-auto sm:px-10" disabled={submitting || roomTypes.length === 0}>
                                            {submitting ? 'Loading...' : 'Continue'}
                                        </button>
                                    </form>
                                </>
                            )}

                            {step === 'addons' && (
                                <>
                                    <StepProgress step={step} />
                                    {error && <p className="text-error text-sm mb-4">{error}</p>}
                                    <form onSubmit={goToGuestPaymentStep} className="flex flex-col gap-6">
                                        {addOns.length > 0 && (
                                            <div className="flex flex-col gap-3">
                                                {addOns.map(addOn => (
                                                    <label key={addOn.id}
                                                           className={`flex items-start gap-3 rounded-lg p-4 border bg-warm-white cursor-pointer ${selectedAddOnIds.includes(addOn.id) ? 'border-2 border-green' : 'border-tan'}`}>
                                                        <input type="checkbox" className="mt-1" checked={selectedAddOnIds.includes(addOn.id)}
                                                               onChange={() => toggleAddOn(addOn.id)} />
                                                        <div className="flex-1">
                                                            <div className="flex justify-between gap-2">
                                                                <span className="font-medium">{addOn.name}</span>
                                                                <span className="text-sm font-medium flex-shrink-0">
                                                                    {displayPrice(addOn.price)}{addOn.billingType === 'PER_NIGHT' ? ' / night' : ''}
                                                                </span>
                                                            </div>
                                                            {addOn.description && <p className="text-sm text-muted mt-1">{addOn.description}</p>}
                                                        </div>
                                                    </label>
                                                ))}
                                            </div>
                                        )}
                                        <div>
                                            <label className="text-sm font-medium block mb-2">Special requests</label>
                                            <textarea value={specialRequests} onChange={e => setSpecialRequests(e.target.value.slice(0, 1000))}
                                                      placeholder="Let us know if there's anything we can do to make your stay better."
                                                      rows={3} className="filter-input w-full resize-none" />
                                            <p className="text-xs text-muted mt-1">We can't guarantee special requests, but we'll do our best.</p>
                                        </div>
                                        <div className="flex gap-3">
                                            <button type="button" onClick={() => setStep('room')} className="btn-secondary">Back</button>
                                            <button type="submit" className="btn-primary" disabled={submitting}>
                                                {submitting ? 'Loading...' : 'Continue'}
                                            </button>
                                        </div>
                                    </form>
                                </>
                            )}

                            {step === 'guestPayment' && (
                                <div className="max-w-md">
                                    <StepProgress step={step} />
                                    {error && <p className="text-error text-sm mb-3">{error}</p>}

                                    <TotalForStayCard selectedRoomType={selectedRoomType} nights={nights}
                                                      nightlyRate={nightlyRate} selectedAddOns={selectedAddOns} estimate={estimate} />

                                    <div className="flex flex-col gap-4 mb-6">
                                        <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">Guest information</h2>
                                        <div className="grid grid-cols-2 gap-3">
                                            <input required placeholder="First name" value={firstName} onChange={e => setFirstName(e.target.value)} className="filter-input" />
                                            <input required placeholder="Last name" value={lastName} onChange={e => setLastName(e.target.value)} className="filter-input" />
                                        </div>
                                        <input required type="email" placeholder="Email" value={email} onChange={e => setEmail(e.target.value)} className="filter-input" />
                                        <input required type="tel" placeholder="Mobile phone (10 digits)" value={phoneNumber}
                                               onChange={e => setPhoneNumber(e.target.value.replace(/\D/g, '').slice(0, 10))} className="filter-input" />
                                        <label className="flex items-start gap-2 text-sm">
                                            <input type="checkbox" checked={smsConsent} onChange={e => setSmsConsent(e.target.checked)} className="mt-1" />
                                            <span>Text me a confirmation and updates about my stay</span>
                                        </label>
                                        {/* Hidden honeypot field: legitimate guests never see or fill this in. */}
                                        <div aria-hidden="true" style={{ position: 'absolute', left: '-9999px', top: 'auto', width: '1px', height: '1px', overflow: 'hidden' }}>
                                            <input type="text" tabIndex={-1} autoComplete="off" name="website" value={website} onChange={e => setWebsite(e.target.value)} />
                                        </div>

                                        <p className="text-xs font-semibold text-muted uppercase tracking-wide mt-2">Billing address</p>
                                        <input required placeholder="Street address" value={billingStreet}
                                               onChange={e => setBillingStreet(e.target.value)} className="filter-input" />
                                        <input placeholder="Apt, suite, etc. (optional)" value={billingStreet2}
                                               onChange={e => setBillingStreet2(e.target.value)} className="filter-input" />
                                        <div className="grid grid-cols-3 gap-3">
                                            <input required placeholder="City" value={billingCity} onChange={e => setBillingCity(e.target.value)} className="filter-input col-span-2" />
                                            <input required placeholder="State" maxLength={2} value={billingState}
                                                   onChange={e => setBillingState(e.target.value.toUpperCase().slice(0, 2))} className="filter-input" />
                                        </div>
                                        <input required placeholder="ZIP code" value={billingZip}
                                               onChange={e => setBillingZip(e.target.value.replace(/\D/g, '').slice(0, 5))} className="filter-input w-32" />
                                    </div>

                                    <h2 className="text-sm font-semibold uppercase tracking-wide text-muted mb-4">Payment</h2>
                                    <AcceptJsCardForm onCapture={handlePayment} onCancel={() => setStep('addons')} submitLabel="Book Reservation"
                                                      disabled={!firstName || !lastName || !email || !/^[0-9]{10}$/.test(phoneNumber)
                                                          || !billingStreet || !billingCity || billingState.length !== 2 || billingZip.length !== 5}>
                                        <div className="flex flex-col gap-3 text-xs text-muted border-t border-tan pt-4 mt-2">
                                            <p>
                                                A valid credit card is required to guarantee this reservation.{' '}
                                                <strong className="text-black">Free cancellation until {cancellationDeadline}.</strong>{' '}
                                                Cancellations after that time, and no-shows, forfeit the first night's charge.
                                            </p>
                                            <p>
                                                By selecting "Book Reservation," you agree to our{' '}
                                                <a href="/terms" target="_blank" rel="noopener noreferrer" className="text-green underline">Terms of Service</a>,{' '}
                                                <a href="/privacy-policy" target="_blank" rel="noopener noreferrer" className="text-green underline">Privacy Policy</a>, and{' '}
                                                <a href="/sms-terms" target="_blank" rel="noopener noreferrer" className="text-green underline">SMS Terms &amp; Conditions</a>.
                                            </p>
                                        </div>
                                    </AcceptJsCardForm>
                                    <p className="text-xs text-muted text-center mt-3">Payments processed securely by Authorize.Net</p>
                                </div>
                            )}
                        </div>

                        {summary}
                    </div>
                ) : confirmation && (
                    <div className="max-w-md mx-auto flex flex-col items-center text-center gap-4 py-10">
                        <div className="w-14 h-14 rounded-full bg-green flex items-center justify-center">
                            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#F0E7DE" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
                        </div>
                        <h1 className="section-title !mb-0">Booking confirmed</h1>
                        <p className="text-muted text-sm">We've texted and emailed your confirmation.</p>
                        <div className="bg-warm-white border border-tan rounded-lg p-4 w-full">
                            <p className="text-xs text-muted uppercase tracking-wide">Confirmation code</p>
                            <p className="text-xl font-semibold" style={{ fontFamily: "'DM Serif Display', serif" }}>{confirmation.confirmationCode}</p>
                        </div>
                        <div className="bg-warm-white border border-tan rounded-lg p-4 w-full text-left text-sm text-muted">
                            {selectedRoomType?.name} &middot; {confirmation.checkInDate} &rarr; {confirmation.checkOutDate}
                        </div>
                        <p className="text-sm text-muted">Questions? Call <a href="tel:+16602587257" className="text-green underline">(660) 258-7257</a></p>
                        <a href="/" className="btn-secondary w-full text-center">Back to Martin House Motel</a>
                    </div>
                )}
            </div>

            {showResetWarning && (
                <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-6"
                     onClick={cancelResetWarning}>
                    <div className="bg-cream rounded-lg shadow-2xl max-w-sm w-full p-6" onClick={e => e.stopPropagation()}>
                        <h2 className="section-title !mb-3">Update your stay?</h2>
                        <p className="text-sm text-black/80 mb-6">Changing your stay details may reset your room and add-on selections.</p>
                        <div className="flex flex-col gap-2">
                            <button type="button" onClick={confirmStayEdit} className="btn-primary">OK</button>
                            <button type="button" onClick={cancelResetWarning} className="btn-secondary">Cancel</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
