import { useEffect, useState } from "react"
import { extendStay, extendStayTerminal, getExtendStayEstimate } from "../api/reservationApi"
import Modal from "./Modal"
import TerminalOrRecordOnlyStep, { AmountBanner } from "./TerminalOrRecordOnlyPayment"

function ExtendStayModal({ reservation, onSaved, onClose }) {
    const [checkOutDate, setCheckOutDate] = useState(reservation.checkOutDate)
    const [estimatedCharge, setEstimatedCharge] = useState(null)
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)
    const [result, setResult] = useState(null)
    const [noCredential, setNoCredential] = useState(false)

    useEffect(() => {
        if (checkOutDate === reservation.checkOutDate) {
            setEstimatedCharge(null)
            return
        }
        let cancelled = false
        getExtendStayEstimate(reservation.id, checkOutDate)
            .then(res => { if (!cancelled) setEstimatedCharge(res.data.total) })
            .catch(() => { if (!cancelled) setEstimatedCharge(null) })
        return () => { cancelled = true }
    }, [checkOutDate])

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
        return (
            <Modal onClose={onClose} size="sm">
                <h2 className="text-lg text-black font-semibold mb-4">No Card on File</h2>
                <p className="text-sm text-muted mb-4">
                    This reservation has no active card on file to charge for the extension. Charge via the front-desk terminal instead.
                </p>

                <TerminalOrRecordOnlyStep
                    amount={estimatedCharge}
                    amountLabel="Amount to collect"
                    onSubmitTerminal={async posDeviceId => {
                        const res = await extendStayTerminal(reservation.id, checkOutDate, posDeviceId)
                        setResult(res.data)
                    }}
                    onCancel={() => setNoCredential(false)}
                    cancelLabel="Back"
                    terminalErrorMessage="Failed to charge terminal."
                    recordOnlyErrorMessage="Failed to record charge."
                />
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
