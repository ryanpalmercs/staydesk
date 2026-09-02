import { useEffect, useState } from "react"
import { extendStay, extendStayTerminal, getReservationEstimate } from "../api/reservationApi"
import { getPosDevices, getPosDeviceConfig, checkPosDeviceHealth } from "../api/posDeviceApi"
import { displayPrice } from "../utils/price"
import Modal from "./Modal"

function AmountBanner({ amount, label }) {
    if (amount == null) return null
    return (
        <div className="flex justify-between items-baseline mb-2">
            <span className="text-sm text-muted">{label}</span>
            <span className="text-lg font-semibold text-black">{displayPrice(amount)}</span>
        </div>
    )
}

function TerminalExtendForm({ reservationId, checkOutDate, chargeAmount, devices, onExtended, onCancel, onError }) {
    const [selectedDeviceId, setSelectedDeviceId] = useState(devices[0]?.id ?? '')
    const [submitting, setSubmitting] = useState(false)
    const [deviceOnline, setDeviceOnline] = useState(null)

    useEffect(() => {
        if (!selectedDeviceId) return
        setDeviceOnline(null)
        let cancelled = false
        checkPosDeviceHealth(selectedDeviceId)
            .then(res => { if (!cancelled) setDeviceOnline(res.data.online) })
            .catch(() => { if (!cancelled) setDeviceOnline(false) })
        return () => { cancelled = true }
    }, [selectedDeviceId])

    async function handleSubmit(e) {
        e.preventDefault()
        setSubmitting(true)
        onError(null)

        try {
            const res = await extendStayTerminal(reservationId, checkOutDate, Number(selectedDeviceId))
            onExtended(res.data)
        } catch (err) {
            const data = err.response?.data
            onError(typeof data === 'string' && data ? data : 'Failed to charge terminal.')
        }

        setSubmitting(false)
    }

    return (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <AmountBanner amount={chargeAmount} label="Will charge on terminal" />
            {devices.length > 1 && (
                <select value={selectedDeviceId} onChange={e => setSelectedDeviceId(e.target.value)} className="filter-input">
                    {devices.map(d => (
                        <option key={d.id} value={d.id}>{d.friendlyName}{d.location ? ` — ${d.location}` : ''}</option>
                    ))}
                </select>
            )}

            {deviceOnline === false && (
                <p className="text-sm text-error">Terminal isn't responding. Try another device.</p>
            )}

            {submitting && (
                <p className="text-sm text-muted text-center py-2">Waiting for guest to tap, dip, or swipe...</p>
            )}

            <div className="flex justify-end gap-3 mt-2">
                <button type="button" onClick={onCancel} className="btn btn-secondary" disabled={submitting}>Back</button>
                <button type="submit" className="btn btn-primary" disabled={submitting || !selectedDeviceId || deviceOnline === false}>
                    {submitting ? 'Waiting on terminal...' : 'Charge on Terminal'}
                </button>
            </div>
        </form>
    )
}

function RecordOnlyExtendForm({ reservationId, checkOutDate, chargeAmount, onExtended, onCancel, onError }) {
    const [submitting, setSubmitting] = useState(false)

    async function handleSubmit(e) {
        e.preventDefault()
        setSubmitting(true)
        onError(null)

        try {
            const res = await extendStayTerminal(reservationId, checkOutDate, null)
            onExtended(res.data)
        } catch (err) {
            const data = err.response?.data
            onError(typeof data === 'string' && data ? data : 'Failed to record charge.')
        }

        setSubmitting(false)
    }

    return (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <AmountBanner amount={chargeAmount} label="Will record charge" />
            <p className="text-sm text-muted">
                No card-present terminal is paired. This records the charge on the folio without processing a real payment.
            </p>
            <div className="flex justify-end gap-3 mt-2">
                <button type="button" onClick={onCancel} className="btn btn-secondary" disabled={submitting}>Back</button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                    {submitting ? 'Recording...' : 'Record Charge (No Terminal)'}
                </button>
            </div>
        </form>
    )
}

function ExtendStayModal({ reservation, onSaved, onClose }) {
    const [checkOutDate, setCheckOutDate] = useState(reservation.checkOutDate)
    const [currentTotal, setCurrentTotal] = useState(null)
    const [newTotal, setNewTotal] = useState(null)
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)
    const [result, setResult] = useState(null)
    const [noCredential, setNoCredential] = useState(false)
    const [posDevices, setPosDevices] = useState([])
    const [cardPresentRecordOnly, setCardPresentRecordOnly] = useState(false)
    const [paymentMode, setPaymentMode] = useState(null)

    useEffect(() => {
        getReservationEstimate({
            rateType: reservation.rateType,
            guestCount: reservation.guestCount,
            checkInDate: reservation.checkInDate,
            checkOutDate: reservation.checkOutDate,
            guestId: reservation.guestId
        }).then(res => setCurrentTotal(res.data.total)).catch(() => setCurrentTotal(null))

        getPosDevices().then(res => setPosDevices(res.data ?? []))
        getPosDeviceConfig().then(res => setCardPresentRecordOnly(res.data.recordOnly))
    }, [])

    useEffect(() => {
        if (checkOutDate === reservation.checkOutDate) {
            setNewTotal(null)
            return
        }
        let cancelled = false
        getReservationEstimate({
            rateType: reservation.rateType,
            guestCount: reservation.guestCount,
            checkInDate: reservation.checkInDate,
            checkOutDate,
            guestId: reservation.guestId
        }).then(res => { if (!cancelled) setNewTotal(res.data.total) }).catch(() => { if (!cancelled) setNewTotal(null) })
        return () => { cancelled = true }
    }, [checkOutDate])

    const estimatedCharge = currentTotal != null && newTotal != null ? newTotal - currentTotal : null

    async function handleSubmit(e) {
        e.preventDefault()
        setError(null)
        setSubmitting(true)

        try {
            const res = await extendStay(reservation.id, checkOutDate)
            setResult(res.data)
        } catch (err) {
            if (err.response?.status === 409) {
                setNoCredential(true)
            } else {
                const data = err.response?.data
                setError(typeof data === 'string' && data ? data : 'Failed to extend stay.')
            }
        }

        setSubmitting(false)
    }

    if (result) {
        return (
            <Modal onClose={onSaved} size="sm">
                <h2 className="text-lg text-black font-semibold mb-4">Stay Extended</h2>
                <p className="text-sm text-black mb-2">New check-out date: {result.reservation.checkOutDate}</p>
                <AmountBanner amount={result.amountCharged} label="Charged" />
                <div className="flex justify-end mt-4">
                    <button type="button" onClick={onSaved} className="btn btn-primary">Done</button>
                </div>
            </Modal>
        )
    }

    if (noCredential) {
        const noDeviceRecordOnly = posDevices.length === 0 && cardPresentRecordOnly

        return (
            <Modal onClose={onClose} size="sm">
                <h2 className="text-lg text-black font-semibold mb-4">No Card on File</h2>
                <p className="text-sm text-muted mb-4">
                    This reservation has no active card on file to charge for the extension. Charge via the front-desk terminal instead.
                </p>
                {error && <p className="text-sm text-error mb-2">{error}</p>}

                {!paymentMode && posDevices.length > 0 && (
                    <div className="flex justify-end gap-3">
                        <button type="button" onClick={() => setNoCredential(false)} className="btn btn-secondary">Back</button>
                        <button type="button" onClick={() => setPaymentMode('terminal')} className="btn btn-primary">Charge on Terminal</button>
                    </div>
                )}

                {!paymentMode && posDevices.length === 0 && noDeviceRecordOnly && (
                    <div className="flex justify-end gap-3">
                        <button type="button" onClick={() => setNoCredential(false)} className="btn btn-secondary">Back</button>
                        <button type="button" onClick={() => setPaymentMode('record')} className="btn btn-primary">Record Charge (No Terminal)</button>
                    </div>
                )}

                {!paymentMode && posDevices.length === 0 && !noDeviceRecordOnly && (
                    <div className="flex flex-col gap-4">
                        <p className="text-sm text-error">No terminal is paired and record-only charging isn't enabled. Contact support.</p>
                        <div className="flex justify-end">
                            <button type="button" onClick={() => setNoCredential(false)} className="btn btn-secondary">Back</button>
                        </div>
                    </div>
                )}

                {paymentMode === 'terminal' && (
                    <TerminalExtendForm
                        reservationId={reservation.id}
                        checkOutDate={checkOutDate}
                        chargeAmount={estimatedCharge}
                        devices={posDevices}
                        onExtended={setResult}
                        onCancel={() => setPaymentMode(null)}
                        onError={setError}
                    />
                )}

                {paymentMode === 'record' && (
                    <RecordOnlyExtendForm
                        reservationId={reservation.id}
                        checkOutDate={checkOutDate}
                        chargeAmount={estimatedCharge}
                        onExtended={setResult}
                        onCancel={() => setPaymentMode(null)}
                        onError={setError}
                    />
                )}
            </Modal>
        )
    }

    return (
        <Modal onClose={onClose} size="sm">
            <h2 className="text-lg text-black font-semibold mb-4">Extend Stay</h2>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <p className="text-sm text-muted">Current check-out: {reservation.checkOutDate}</p>

                <div>
                    <label className="block text-sm text-muted mb-1">New Check-Out Date</label>
                    <input
                        type="date"
                        value={checkOutDate}
                        min={reservation.checkOutDate}
                        onChange={e => setCheckOutDate(e.target.value)}
                        className="filter-input"
                        required
                    />
                </div>

                <AmountBanner amount={estimatedCharge} label="Will charge card on file" />

                {error && <p className="text-sm text-error">{error}</p>}

                <div className="flex justify-end gap-3 mt-2">
                    <button type="button" onClick={onClose} className="btn btn-secondary">Cancel</button>
                    <button type="submit" className="btn btn-primary" disabled={submitting || checkOutDate === reservation.checkOutDate}>
                        {submitting ? 'Extending...' : 'Extend'}
                    </button>
                </div>
            </form>
        </Modal>
    )
}

export default ExtendStayModal
