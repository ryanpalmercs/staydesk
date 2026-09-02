import { useEffect, useState } from "react"
import { extendStay, getReservationEstimate } from "../api/reservationApi"
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

function ExtendStayModal({ reservation, onSaved, onClose }) {
    const [checkOutDate, setCheckOutDate] = useState(reservation.checkOutDate)
    const [currentTotal, setCurrentTotal] = useState(null)
    const [newTotal, setNewTotal] = useState(null)
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)
    const [result, setResult] = useState(null)

    useEffect(() => {
        getReservationEstimate({
            rateType: reservation.rateType,
            guestCount: reservation.guestCount,
            checkInDate: reservation.checkInDate,
            checkOutDate: reservation.checkOutDate,
            guestId: reservation.guestId
        }).then(res => setCurrentTotal(res.data.total)).catch(() => setCurrentTotal(null))
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
            const data = err.response?.data
            setError(typeof data === 'string' && data ? data : 'Failed to extend stay.')
        }

        setSubmitting(false)
    }

    if (result) {
        return (
            <Modal onClose={onSaved} size="sm">
                <h2 className="text-lg text-black font-semibold mb-4">Stay Extended</h2>
                <p className="text-sm text-black mb-2">New check-out date: {result.reservation.checkOutDate}</p>
                <AmountBanner amount={result.amountCharged} label="Charged to card on file" />
                <div className="flex justify-end mt-4">
                    <button type="button" onClick={onSaved} className="btn btn-primary">Done</button>
                </div>
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
