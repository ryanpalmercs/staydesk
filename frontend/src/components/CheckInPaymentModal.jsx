import { useEffect, useState } from "react"
import { CardElement, Elements, useElements, useStripe } from "@stripe/react-stripe-js"
import { loadStripe } from "@stripe/stripe-js"
import { getConnectStatus } from "../api/stripeApi"
import { getPropertySetting } from "../api/settingsApi"
import { getAvailableRoomsForCheckIn } from "../api/reservationApi"
import { getPosDevices, checkPosDeviceHealth } from "../api/posDeviceApi"
import AcceptJsCardForm from "./AcceptJsCardForm"
import DoorCode from "./DoorCode"
import { stripeCardElementOptions } from "../utils/stripeCardElementStyle"
import Modal from "./Modal"

function RoomPicker({ reservationId, onRoomChosen, onClose }) {
    const [rooms, setRooms] = useState([])
    const [loading, setLoading] = useState(true)
    const [selectedRoomId, setSelectedRoomId] = useState('')

    useEffect(() => {
        getAvailableRoomsForCheckIn(reservationId).then(res => {
            setRooms(res.data ?? [])
            setLoading(false)
        })
    }, [reservationId])

    function handleSubmit(e) {
        e.preventDefault()
        onRoomChosen(Number(selectedRoomId))
    }

    if (loading) {
        return <p className="text-sm text-muted">Loading available rooms...</p>
    }

    if (rooms.length === 0) {
        return (
            <div className="flex flex-col gap-4">
                <p className="text-sm text-error">No rooms of this type are currently available.</p>
                <div className="flex justify-end">
                    <button type="button" onClick={onClose} className="btn btn-secondary">Close</button>
                </div>
            </div>
        )
    }

    return (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="flex flex-col gap-2 max-h-64 overflow-y-auto">
                {rooms.map(room => (
                    <label key={room.id} className="filter-input flex items-center gap-2 cursor-pointer">
                        <input
                            type="radio"
                            name="roomId"
                            value={room.id}
                            checked={Number(selectedRoomId) === room.id}
                            onChange={() => setSelectedRoomId(room.id)}
                        />
                        Room {room.roomNumber}
                    </label>
                ))}
            </div>
            <div className="flex justify-end gap-3 mt-2">
                <button type="button" onClick={onClose} className="btn btn-secondary">Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={!selectedRoomId}>Continue</button>
            </div>
        </form>
    )
}

function DoorAccessFailedNotice({ onClose }) {
    return (
        <div className="flex flex-col gap-4">
            <p className="text-sm text-error font-medium">Door lock code couldn't be issued</p>
            <p className="text-sm text-muted">
                Guest has been checked in, but the smart lock didn't respond. Please give the guest a
                physical key at the front desk. We'll keep retrying in the background and notify front
                desk staff if the code goes through.
            </p>
            <div className="flex justify-end mt-2">
                <button type="button" onClick={onClose} className="btn btn-primary">
                    Got it
                </button>
            </div>
        </div>
    )
}

function CheckInPaymentForm({ roomId, onConfirm, onClose, onCheckedIn }) {
    const stripe = useStripe()
    const elements = useElements()
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState(null)
    const [slowNotice, setSlowNotice] = useState(false)
    const [doorAccessFailed, setDoorAccessFailed] = useState(false)

    async function handleSubmit(e) {
        e.preventDefault()

        if (!stripe || !elements) {
            return
        }

        setSubmitting(true)
        setError(null)

        const card = elements.getElement(CardElement)

        const incidentalsResult = await stripe.createPaymentMethod({ type: 'card', card })
        if (incidentalsResult.error) {
            setError(incidentalsResult.error.message)
            setSubmitting(false)
            return
        }

        const slowTimer = setTimeout(() => setSlowNotice(true), 2000)

        try {
            const doorAccessStatus = await onConfirm(roomId, incidentalsResult.paymentMethod.id)
            if (doorAccessStatus === 'FAILED') {
                setDoorAccessFailed(true)
            } else {
                onCheckedIn(doorAccessStatus)
            }
        } catch (err) {
            setError('Failed to check in. Please try a different card.')
        } finally {
            clearTimeout(slowTimer)
            setSubmitting(false)
            setSlowNotice(false)
        }
    }

    if (doorAccessFailed) {
        return <DoorAccessFailedNotice onClose={onClose} />
    }

    return (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="filter-input">
                <CardElement options={stripeCardElementOptions} />
            </div>

            {error && <p className="text-sm text-error">{error}</p>}

            <div className="flex justify-end gap-3 mt-2">
                <button type="button" onClick={onClose} className="btn btn-secondary" disabled={submitting}>
                    Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={!stripe || submitting}>
                    {submitting ? (slowNotice ? 'Still setting up door access...' : 'Checking in...') : 'Check In'}
                </button>
            </div>
        </form>
    )
}

function AcceptJsCheckInForm({ roomId, reservationChannel, onConfirm, onClose, onCheckedIn }) {
    const [doorAccessFailed, setDoorAccessFailed] = useState(false)
    const isWalkIn = reservationChannel === 'WALK_IN'

    async function handleCapture(incidentalsToken, roomToken) {
        const doorAccessStatus = await onConfirm(roomId, incidentalsToken, roomToken)
        if (doorAccessStatus === 'FAILED') {
            setDoorAccessFailed(true)
        } else {
            onCheckedIn(doorAccessStatus)
        }
    }

    if (doorAccessFailed) {
        return <DoorAccessFailedNotice onClose={onClose} />
    }

    return <AcceptJsCardForm onCapture={handleCapture} onCancel={onClose} submitLabel="Check In" dual={isWalkIn} />
}

function TerminalCheckInForm({ roomId, onConfirmTerminal, onClose, onCheckedIn, devices, onHealthCheck }) {
    const [selectedDeviceId, setSelectedDeviceId] = useState(devices[0]?.id ?? '')
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState(null)
    const [doorAccessFailed, setDoorAccessFailed] = useState(false)
    const [deviceOnline, setDeviceOnline] = useState(null)

    useEffect(() => {
        if (!selectedDeviceId) return
        setDeviceOnline(null)
        let cancelled = false
        checkPosDeviceHealth(selectedDeviceId)
            .then(res => {
                if (cancelled) return
                setDeviceOnline(res.data.online)
                onHealthCheck(res.data.online)
            })
            .catch(() => {
                if (cancelled) return
                setDeviceOnline(false)
                onHealthCheck(false)
            })
        return () => { cancelled = true }
    }, [selectedDeviceId])

    async function handleSubmit(e) {
        e.preventDefault()
        setSubmitting(true)
        setError(null)

        try {
            const doorAccessStatus = await onConfirmTerminal(roomId, Number(selectedDeviceId))
            if (doorAccessStatus === 'FAILED') {
                setDoorAccessFailed(true)
            } else {
                onCheckedIn(doorAccessStatus)
            }
        } catch (err) {
            setError('Failed to check in. The card may have been declined on the terminal.')
        }

        setSubmitting(false)
    }

    if (doorAccessFailed) {
        return <DoorAccessFailedNotice onClose={onClose} />
    }

    return (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {devices.length > 1 && (
                <select value={selectedDeviceId} onChange={e => setSelectedDeviceId(e.target.value)} className="filter-input">
                    {devices.map(d => (
                        <option key={d.id} value={d.id}>{d.friendlyName}{d.location ? ` — ${d.location}` : ''}</option>
                    ))}
                </select>
            )}

            {deviceOnline === false && (
                <p className="text-sm text-error">Terminal isn't responding. Try another device or enter the card manually.</p>
            )}

            {submitting && (
                <p className="text-sm text-muted text-center py-4">
                    Waiting for guest to tap, dip, or swipe on {devices.find(d => d.id === Number(selectedDeviceId))?.friendlyName}...
                </p>
            )}

            {error && <p className="text-sm text-error">{error}</p>}

            <div className="flex justify-end gap-3 mt-2">
                <button type="button" onClick={onClose} className="btn btn-secondary" disabled={submitting}>
                    Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting || !selectedDeviceId || deviceOnline === false}>
                    {submitting ? 'Waiting on terminal...' : 'Charge on Terminal'}
                </button>
            </div>
        </form>
    )
}

function CheckInPaymentModal({ reservationId, reservationChannel, onConfirm, onConfirmTerminal, onClose }) {
    const [step, setStep] = useState('room')
    const [selectedRoomId, setSelectedRoomId] = useState(null)
    const [stripePromise, setStripePromise] = useState(null)
    const [provider, setProvider] = useState(null)
    const [error, setError] = useState(null)
    const [posDevices, setPosDevices] = useState([])
    const [useTerminal, setUseTerminal] = useState(false)
    const [manualEntryUnlocked, setManualEntryUnlocked] = useState(false)

    useEffect(() => {
        getPropertySetting('payment_provider').then(res => {
            setProvider(res.data.value)

            if (res.data.value === 'stripe') {
                getConnectStatus().then(connectRes => {
                    if (connectRes.data.connected) {
                        setStripePromise(loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY, {
                            stripeAccount: connectRes.data.accountId
                        }))
                    } else {
                        setError('Stripe is not connected. Connect an account in Settings first.')
                    }
                })
            }
        })

        getPosDevices().then(res => {
            const devices = res.data ?? []
            setPosDevices(devices)
            setUseTerminal(devices.length > 0)
        })
    }, [])

    useEffect(() => {
        if (!useTerminal) return
        setManualEntryUnlocked(false)
        const timer = setTimeout(() => setManualEntryUnlocked(true), 4000)
        return () => clearTimeout(timer)
    }, [useTerminal])

    function handleHealthCheck(online) {
        if (!online) setManualEntryUnlocked(true)
    }

    function handleRoomChosen(roomId) {
        setSelectedRoomId(roomId)
        setStep('payment')
    }

    function handleCheckedIn(doorAccessStatus) {
        if (doorAccessStatus === 'ISSUED') {
            setStep('code')
        } else {
            onClose()
        }
    }

    const hasManualProvider = provider === 'authorizenet' || (provider === 'stripe' && stripePromise)

    return (
        <Modal onClose={onClose} size="md">
                <h2 className="text-lg text-black font-semibold mb-4">
                    {step === 'room' ? 'Assign a Room' : step === 'code' ? 'Door Code' : 'Card for Incidentals'}
                </h2>

                {step === 'room' && (
                    <RoomPicker reservationId={reservationId} onRoomChosen={handleRoomChosen} onClose={onClose} />
                )}

                {step === 'payment' && (
                    <>
                        <p className="text-sm text-muted mb-4">
                            {reservationChannel === 'WALK_IN'
                                ? "We'll charge the full stay now, then place a small hold for incidentals."
                                : "We'll place a hold on this card as an incidentals buffer. It won't be charged unless needed at checkout."}
                        </p>
                        {error && <p className="text-sm text-error mb-4">{error}</p>}

                        {posDevices.length > 0 && hasManualProvider && (
                            <div className="flex gap-2 justify-center mb-4">
                                <button type="button" onClick={() => setUseTerminal(true)} className={`filter-btn${useTerminal ? ' active' : ''}`}>
                                    Charge on Terminal
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setUseTerminal(false)}
                                    disabled={!manualEntryUnlocked}
                                    className={`filter-btn${!useTerminal ? ' active' : ''}`}
                                >
                                    Enter Card Manually
                                </button>
                            </div>
                        )}

                        {useTerminal && posDevices.length > 0 && (
                            <TerminalCheckInForm
                                roomId={selectedRoomId}
                                onConfirmTerminal={onConfirmTerminal}
                                onClose={onClose}
                                onCheckedIn={handleCheckedIn}
                                devices={posDevices}
                                onHealthCheck={handleHealthCheck}
                            />
                        )}
                        {!useTerminal && provider === 'authorizenet' && (
                            <AcceptJsCheckInForm roomId={selectedRoomId} reservationChannel={reservationChannel} onConfirm={onConfirm} onClose={onClose} onCheckedIn={handleCheckedIn} />
                        )}
                        {!useTerminal && provider === 'stripe' && stripePromise && (
                            <Elements stripe={stripePromise}>
                                <CheckInPaymentForm roomId={selectedRoomId} onConfirm={onConfirm} onClose={onClose} onCheckedIn={handleCheckedIn} />
                            </Elements>
                        )}
                        {!useTerminal && posDevices.length > 0 && !hasManualProvider && (
                            <p className="text-sm text-error">Terminal unavailable and no backup card entry is configured. Contact support.</p>
                        )}
                    </>
                )}

                {step === 'code' && (
                    <div className="flex flex-col gap-4">
                        <p className="text-sm text-muted">Give this code to the guest for door access.</p>
                        <DoorCode reservationId={reservationId} />
                        <div className="flex justify-end mt-2">
                            <button onClick={onClose} className="btn btn-primary">Done</button>
                        </div>
                    </div>
                )}
        </Modal>
    )
}

export default CheckInPaymentModal
