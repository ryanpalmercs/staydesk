import { useEffect, useState } from "react"
import { getPropertySetting } from "../api/settingsApi"
import { getAvailableRoomsForCheckIn, getReservationEstimate } from "../api/reservationApi"
import { getFolioPayments, settleWalkInStay, settleWalkInStayTerminal } from "../api/folioApi"
import DoorCode from "./DoorCode"
import Modal from "./Modal"
import ConfirmDialog from "./ConfirmDialog"
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

function CheckInPaymentModal({ reservationId, reservation, onConfirm, onConfirmTerminal, onClose, onCancelReservation }) {
    const reservationChannel = reservation.channel
    const isWalkIn = reservationChannel === 'WALK_IN'

    const [step, setStep] = useState(isWalkIn ? 'checking' : 'room')
    const [selectedRoomId, setSelectedRoomId] = useState(null)
    const [incidentalsHoldAmount, setIncidentalsHoldAmount] = useState(null)
    const [stayTotal, setStayTotal] = useState(null)
    const [confirmingCancel, setConfirmingCancel] = useState(false)

    useEffect(() => {
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

            getFolioPayments(reservation.folioId).then(res => {
                const settled = (res.data ?? []).some(p => p.kind === 'ROOM' && p.status === 'CAPTURED')
                setStep(settled ? 'room' : 'settle')
            }).catch(() => setStep('settle'))
        }
    }, [])

    const chargeAmount = incidentalsHoldAmount
    const chargeLabel = 'Incidentals Hold'

    function handleRoomChosen(roomId) {
        setSelectedRoomId(roomId)
        setStep('payment')
    }

    function handleSettled() {
        setStep('room')
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
        if (isWalkIn) {
            setConfirmingCancel(true)
            return
        }
        onClose()
    }

    async function confirmCancelReservation() {
        setConfirmingCancel(false)
        await onCancelReservation()
        onClose()
    }

    return (
        <Modal onClose={onClose} size="md">
            <h2 className="text-lg text-black font-semibold mb-4">
                {step === 'checking' ? 'Loading...' : step === 'settle' ? 'Charge for Stay' : step === 'room' ? 'Assign a Room' : step === 'code' ? 'Door Code' : 'Card for Incidentals'}
            </h2>

            {step === 'checking' && (
                <p className="text-sm text-muted">Loading...</p>
            )}

            {step === 'room' && (
                <RoomPicker reservationId={reservationId} onRoomChosen={handleRoomChosen} onClose={handleCancelClick} />
            )}

            {step === 'settle' && (
                <PaymentMethodStep
                    amount={stayTotal}
                    amountLabel="Total Charge"
                    description="Charge the full stay for this booking now, before continuing."
                    dual={false}
                    submitLabel="Charge"
                    onSubmitToken={async (roomToken) => {
                        await settleWalkInStay(reservation.folioId, roomToken)
                        handleSettled()
                    }}
                    onSubmitTerminal={async (deviceId) => {
                        await settleWalkInStayTerminal(reservation.folioId, deviceId)
                        handleSettled()
                    }}
                    onCancel={handleCancelClick}
                    terminalErrorMessage="Failed to charge card. It may have been declined on the terminal."
                    recordOnlyErrorMessage="Failed to charge card."
                />
            )}

            {step === 'payment' && (
                <PaymentMethodStep
                    amount={chargeAmount}
                    amountLabel={chargeLabel}
                    description="We'll place a hold on this card as an incidentals buffer. It won't be charged unless needed at checkout."
                    dual={false}
                    submitLabel="Check In"
                    onSubmitToken={async (incidentalsToken) => {
                        const doorAccessStatus = await onConfirm(selectedRoomId, incidentalsToken)
                        handleCheckedIn(doorAccessStatus)
                    }}
                    onSubmitTerminal={async (deviceId) => {
                        const doorAccessStatus = await onConfirmTerminal(selectedRoomId, deviceId)
                        handleCheckedIn(doorAccessStatus)
                    }}
                    onCancel={handleCancelClick}
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