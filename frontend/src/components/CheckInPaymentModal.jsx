import { useEffect, useState } from "react"
import { getPropertySetting } from "../api/settingsApi"
import { getAvailableRoomsForCheckIn, getCheckInEstimate } from "../api/reservationApi"
import DoorCode from "./DoorCode"
import Modal from "./Modal"
import PaymentMethodStep from "./PaymentMethodStep"

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

function formatDate(str) {
    return new Date(str + 'T12:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function daysUntil(dateString) {
    const now = new Date()
    const todayString = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    const diffMs = new Date(dateString + 'T00:00:00') - new Date(todayString + 'T00:00:00')
    return Math.round(diffMs / 86400000)
}

function FutureCheckInWarning({ checkInDate, daysOut, showChargeWarning, onCancel, onConfirm }) {
    return (
        <div className="flex flex-col gap-4">
            <div className="rounded-md border border-error/40 bg-error/5 p-4">
                <p className="text-sm font-semibold text-error mb-2">
                    This reservation isn't due to check in until {formatDate(checkInDate)} ({daysOut} day{daysOut === 1 ? '' : 's'} from now).
                </p>
                <p className="text-sm text-error mb-2">Checking in now will immediately:</p>
                <ul className="text-sm text-error list-disc list-inside space-y-1">
                    <li>Assign a room to this guest</li>
                    {showChargeWarning && <li>Charge the full stay to their card</li>}
                </ul>
            </div>
            <div className="flex justify-end gap-3 mt-2">
                <button type="button" onClick={onCancel} className="btn btn-secondary">Cancel</button>
                <button type="button" onClick={onConfirm} className="btn btn-primary">Check In Anyway</button>
            </div>
        </div>
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

function CheckInPaymentModal({ reservationId, reservation, onConfirm, onConfirmTerminal, onClose }) {
    const daysOut = daysUntil(reservation.checkInDate)
    const isFutureCheckIn = daysOut > 0

    const [step, setStep] = useState(isFutureCheckIn ? 'future-warning' : 'room')
    const [selectedRoomId, setSelectedRoomId] = useState(null)
    const [incidentalsHoldAmount, setIncidentalsHoldAmount] = useState(null)
    const [stayTotal, setStayTotal] = useState(null)
    const [roomChargeDue, setRoomChargeDue] = useState(false)

    useEffect(() => {
        getPropertySetting('incidentals_hold_amount').then(res => {
            setIncidentalsHoldAmount(res.data.value)
        })

        getCheckInEstimate(reservationId).then(res => {
            setStayTotal(res.data.total)
            setRoomChargeDue(res.data.roomChargeDue)
        }).catch(() => setStayTotal(null))
    }, [])

    const chargeAmount = roomChargeDue
        ? (stayTotal != null && incidentalsHoldAmount != null ? stayTotal + parseFloat(incidentalsHoldAmount) : null)
        : incidentalsHoldAmount
    const chargeLabel = roomChargeDue ? 'Total Charge' : 'Incidentals Hold'

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

    function handleFutureWarningConfirmed() {
        setStep('room')
    }

    return (
        <Modal onClose={onClose} size="md">
            <h2 className="text-lg text-black font-semibold mb-4">
                {step === 'future-warning' ? 'Confirm Early Check-In' : step === 'room' ? 'Assign a Room' : step === 'code' ? 'Door Code' : 'Card for Incidentals'}
            </h2>

            {step === 'future-warning' && (
                <FutureCheckInWarning
                    checkInDate={reservation.checkInDate}
                    daysOut={daysOut}
                    showChargeWarning={roomChargeDue}
                    onCancel={onClose}
                    onConfirm={handleFutureWarningConfirmed}
                />
            )}

            {step === 'room' && (
                <RoomPicker reservationId={reservationId} onRoomChosen={handleRoomChosen} onClose={onClose} />
            )}

            {step === 'payment' && (
                <PaymentMethodStep
                    amount={chargeAmount}
                    amountLabel={chargeLabel}
                    description={roomChargeDue
                        ? "We'll charge the full stay now, then place a small hold for incidentals."
                        : "We'll place a hold on this card as an incidentals buffer. It won't be charged unless needed at checkout."}
                    dual={roomChargeDue}
                    submitLabel="Check In"
                    onSubmitToken={async (incidentalsToken, roomToken) => {
                        const doorAccessStatus = await onConfirm(selectedRoomId, incidentalsToken, roomToken)
                        handleCheckedIn(doorAccessStatus)
                    }}
                    onSubmitTerminal={async (deviceId) => {
                        const doorAccessStatus = await onConfirmTerminal(selectedRoomId, deviceId)
                        handleCheckedIn(doorAccessStatus)
                    }}
                    onCancel={onClose}
                    terminalErrorMessage="Failed to check in. The card may have been declined on the terminal."
                    recordOnlyErrorMessage="Failed to check in."
                />
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
        </Modal>
    )
}

export default CheckInPaymentModal
