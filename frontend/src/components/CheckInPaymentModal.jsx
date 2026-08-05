import { useEffect, useState } from "react"
import { getPropertySetting } from "../api/settingsApi"
import { getAvailableRoomsForCheckIn, getReservationEstimate } from "../api/reservationApi"
import { getPosDevices, getPosDeviceConfig, checkPosDeviceHealth } from "../api/posDeviceApi"
import { displayPrice } from "../utils/price"
import AcceptJsCardForm from "./AcceptJsCardForm"
import DoorCode from "./DoorCode"
import Modal from "./Modal"
import ConfirmDialog from "./ConfirmDialog"

function AmountBanner({ amount, label }) {
    if (amount == null) return null
    return (
        <div className="flex justify-between items-baseline mb-2">
            <span className="text-sm text-muted">{label}</span>
            <span className="text-lg font-semibold text-black">{displayPrice(amount)}</span>
        </div>
    )
}

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

function AcceptJsCheckInForm({ roomId, reservationChannel, onConfirm, onClose, onCancel, onCheckedIn, chargeAmount, chargeLabel }) {
    const isWalkIn = reservationChannel === 'WALK_IN'

    async function handleCapture(incidentalsToken, roomToken) {
        const doorAccessStatus = await onConfirm(roomId, incidentalsToken, roomToken)
        onCheckedIn(doorAccessStatus)
    }

    return <AcceptJsCardForm onCapture={handleCapture} onCancel={onCancel} submitLabel="Check In" dual={isWalkIn} amount={chargeAmount} label={chargeLabel} />
}

function TerminalCheckInForm({ roomId, onConfirmTerminal, onClose, onCancel, onCheckedIn, devices, onHealthCheck, chargeAmount, chargeLabel }) {
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
            <AmountBanner amount={chargeAmount} label={chargeLabel} />
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
                <button type="button" onClick={onCancel} className="btn btn-secondary" disabled={submitting}>
                    Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting || !selectedDeviceId || deviceOnline === false}>
                    {submitting ? 'Waiting on terminal...' : 'Charge on Terminal'}
                </button>
            </div>
        </form>
    )
}

function RecordOnlyCheckInForm({ roomId, onConfirmTerminal, onClose, onCancel, onCheckedIn, chargeAmount, chargeLabel }) {
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState(null)
    const [doorAccessFailed, setDoorAccessFailed] = useState(false)

    async function handleSubmit(e) {
        e.preventDefault()
        setSubmitting(true)
        setError(null)

        try {
            const doorAccessStatus = await onConfirmTerminal(roomId, null)
            if (doorAccessStatus === 'FAILED') {
                setDoorAccessFailed(true)
            } else {
                onCheckedIn(doorAccessStatus)
            }
        } catch (err) {
            setError('Failed to check in.')
        }

        setSubmitting(false)
    }

    if (doorAccessFailed) {
        return <DoorAccessFailedNotice onClose={onClose} />
    }

    return (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <AmountBanner amount={chargeAmount} label={chargeLabel} />
            <p className="text-sm text-muted">
                No card-present terminal is paired. This records the charge on the folio without processing a real payment.
            </p>

            {error && <p className="text-sm text-error">{error}</p>}

            <div className="flex justify-end gap-3 mt-2">
                <button type="button" onClick={onCancel} className="btn btn-secondary" disabled={submitting}>
                    Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                    {submitting ? 'Recording...' : 'Record Charge (No Terminal)'}
                </button>
            </div>
        </form>
    )
}

function CheckInPaymentModal({ reservationId, reservation, onConfirm, onConfirmTerminal, onClose, onCancelReservation }) {
    const reservationChannel = reservation.channel
    const isWalkIn = reservationChannel === 'WALK_IN'

    const [step, setStep] = useState('room')
    const [selectedRoomId, setSelectedRoomId] = useState(null)
    const [provider, setProvider] = useState(null)
    const [error, setError] = useState(null)
    const [posDevices, setPosDevices] = useState([])
    const [cardPresentRecordOnly, setCardPresentRecordOnly] = useState(false)
    const [useTerminal, setUseTerminal] = useState(false)
    const [manualEntryUnlocked, setManualEntryUnlocked] = useState(false)
    const [incidentalsHoldAmount, setIncidentalsHoldAmount] = useState(null)
    const [stayTotal, setStayTotal] = useState(null)
    const [confirmingCancel, setConfirmingCancel] = useState(false)

    useEffect(() => {
        getPropertySetting('payment_provider').then(res => {
            setProvider(res.data.value)
        })

        getPropertySetting('incidentals_hold_amount').then(res => {
            setIncidentalsHoldAmount(res.data.value)
        })

        if (isWalkIn) {
            getReservationEstimate({
                rateType: reservation.rateType,
                guestCount: reservation.guestCount,
                checkInDate: reservation.checkInDate,
                checkOutDate: reservation.checkOutDate
            }).then(res => setStayTotal(res.data.total)).catch(() => setStayTotal(null))
        }

        getPosDevices().then(res => {
            const devices = res.data ?? []
            setPosDevices(devices)
            setUseTerminal(devices.length > 0)
        })

        getPosDeviceConfig().then(res => setCardPresentRecordOnly(res.data.recordOnly))
    }, [])

    const chargeAmount = isWalkIn
        ? (stayTotal != null && incidentalsHoldAmount != null ? stayTotal + parseFloat(incidentalsHoldAmount) : null)
        : incidentalsHoldAmount
    const chargeLabel = isWalkIn ? 'Total Charge' : 'Incidentals Hold'

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
        } else if (doorAccessStatus === 'FAILED') {
            setStep('door-failed')
        } else {
            onClose()
        }
    }

    function handleCancelClick() {
        if (!isWalkIn) {
            onClose()
            return
        }
        setConfirmingCancel(true)
    }

    async function confirmCancelReservation() {
        setConfirmingCancel(false)
        await onCancelReservation()
        onClose()
    }

    const hasManualProvider = provider === 'authorizenet'
    const noDeviceRecordOnly = posDevices.length === 0 && cardPresentRecordOnly

    return (
        <Modal onClose={onClose} size="md">
            <h2 className="text-lg text-black font-semibold mb-4">
                {step === 'room' ? 'Assign a Room' : step === 'code' ? 'Door Code' : 'Card for Incidentals'}
            </h2>

            {step === 'room' && (
                <RoomPicker reservationId={reservationId} onRoomChosen={handleRoomChosen} onClose={handleCancelClick} />
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
                            onCancel={handleCancelClick}
                            onCheckedIn={handleCheckedIn}
                            devices={posDevices}
                            onHealthCheck={handleHealthCheck}
                            chargeAmount={chargeAmount}
                            chargeLabel={chargeLabel}
                        />
                    )}
                    {noDeviceRecordOnly && (
                        <RecordOnlyCheckInForm
                            roomId={selectedRoomId}
                            onConfirmTerminal={onConfirmTerminal}
                            onClose={onClose}
                            onCancel={handleCancelClick}
                            onCheckedIn={handleCheckedIn}
                            chargeAmount={chargeAmount}
                            chargeLabel={chargeLabel}
                        />
                    )}
                    {!useTerminal && !noDeviceRecordOnly && provider === 'authorizenet' && (
                        <AcceptJsCheckInForm roomId={selectedRoomId} reservationChannel={reservationChannel} onConfirm={onConfirm} onClose={onClose} onCancel={handleCancelClick} onCheckedIn={handleCheckedIn} chargeAmount={chargeAmount} chargeLabel={chargeLabel} />
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

            {step === 'door-failed' && (
                <DoorAccessFailedNotice onClose={onClose} />
            )}

            {confirmingCancel && (
                <ConfirmDialog
                    message="Cancel this walk-in reservation? It will be marked as cancelled."
                    cancelLabel="Keep Going"
                    confirmLabel="Yes, Cancel"
                    onCancel={() => setConfirmingCancel(false)}
                    onConfirm={confirmCancelReservation}
                />
            )}
        </Modal>
    )
}

export default CheckInPaymentModal
