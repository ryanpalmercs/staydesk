import { useEffect, useState } from "react"
import { getPosDevices, getPosDeviceConfig, checkPosDeviceHealth } from "../api/posDeviceApi"
import { displayPrice } from "../utils/price"

export function AmountBanner({ amount, label }) {
    if (amount == null) {
        return null
    }

    return (
        <div className="flex justify-between items-baseline mb-2">
            <span className="text-sm text-muted">{label}</span>
            <span className="text-lg font-semibold text-black">{displayPrice(amount)}</span>
        </div>
    )
}

export function TerminalPaymentForm({ onSubmitTerminal, onCancel, cancelLabel = "Cancel", devices, onHealthCheck,
                                      amount, amountLabel, errorMessage }) {
    const [selectedDeviceId, setSelectedDeviceId] = useState(devices[0]?.id ?? '')
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState(null)
    const [deviceOnline, setDeviceOnline] = useState(null)

    useEffect(() => {
        if (!selectedDeviceId) {
            return
        }

        setDeviceOnline(true)

        let cancelled = false

        checkPosDeviceHealth(selectedDeviceId)
            .then(res => {
                if (cancelled) {
                    return
                }

                setDeviceOnline(res.data.online)
                onHealthCheck?.(res.data.online)
            })
            .catch(() => {
                if (cancelled) {
                    return
                }

                setDeviceOnline(false)
                onHealthCheck?.(false)
            })

        return () => { cancelled = true }
    }, [selectedDeviceId])

    async function handleSubmit(e) {
        e.preventDefault()
        setSubmitting(true)
        setError(null)

        try {
            await onSubmitTerminal(Number(selectedDeviceId))
        } catch (err) {
            setError(errorMessage)
        }

        setSubmitting(false)
    }

    return (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <AmountBanner amount={amount} label={amountLabel} />
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
                    {cancelLabel}
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting || !selectedDeviceId || deviceOnline === false}>
                    {submitting ? 'Waiting on terminal...' : 'Charge on Terminal'}
                </button>
            </div>
        </form>
    )
}

export function RecordOnlyPaymentForm({ onSubmitTerminal, onCancel, cancelLabel = "Cancel", amount, amountLabel, errorMessage }) {
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState(null)

    async function handleSubmit(e) {
        e.preventDefault()
        setSubmitting(true)
        setError(null)

        try {
            await onSubmitTerminal(null)
        } catch (err) {
            setError(errorMessage)
        }

        setSubmitting(false)
    }

    return (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <AmountBanner amount={amount} label={amountLabel} />
            <p className="text-sm text-muted">
                No card-present terminal is paired. This records the charge on the folio without processing a real payment.
            </p>

            {error && <p className="text-sm text-error">{error}</p>}

            <div className="flex justify-end gap-3 mt-2">
                <button type="button" onClick={onCancel} className="btn btn-secondary" disabled={submitting}>
                    {cancelLabel}
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                    {submitting ? 'Recording...' : 'Record Charge (No Terminal)'}
                </button>
            </div>
        </form>
    )
}

/**
 * Self-contained "charge via terminal, or record without one" choice: fetches paired POS
 * devices and record-only config itself, then picks the right form (or an unavailable-both
 * message) without the caller needing to know about either. For a caller that already has
 * this data (e.g. because it also offers a manual-card-entry fallback), use TerminalPaymentForm/
 * RecordOnlyPaymentForm directly instead to avoid fetching it twice.
 */
function TerminalOrRecordOnlyStep({ amount, amountLabel = 'Amount', onSubmitTerminal, onCancel, cancelLabel = "Cancel",
                                    terminalErrorMessage = 'Payment failed. The card may have been declined on the terminal.',
                                    recordOnlyErrorMessage = 'Payment failed.' }) {
    const [posDevices, setPosDevices] = useState([])
    const [cardPresentRecordOnly, setCardPresentRecordOnly] = useState(false)
    const [loaded, setLoaded] = useState(false)

    useEffect(() => {
        Promise.all([getPosDevices(), getPosDeviceConfig()]).then(([devicesRes, configRes]) => {
            setPosDevices(devicesRes.data ?? [])
            setCardPresentRecordOnly(configRes.data.recordOnly)
            setLoaded(true)
        })
    }, [])

    if (!loaded) {
        return null
    }

    if (posDevices.length > 0) {
        return (
            <TerminalPaymentForm
                onSubmitTerminal={onSubmitTerminal}
                onCancel={onCancel}
                cancelLabel={cancelLabel}
                devices={posDevices}
                amount={amount}
                amountLabel={amountLabel}
                errorMessage={terminalErrorMessage}
            />
        )
    }

    if (cardPresentRecordOnly) {
        return (
            <RecordOnlyPaymentForm
                onSubmitTerminal={onSubmitTerminal}
                onCancel={onCancel}
                cancelLabel={cancelLabel}
                amount={amount}
                amountLabel={amountLabel}
                errorMessage={recordOnlyErrorMessage}
            />
        )
    }

    return (
        <div className="flex flex-col gap-4">
            <p className="text-sm text-error">No terminal is paired and record-only charging isn't enabled. Contact support.</p>
            <div className="flex justify-end">
                <button type="button" onClick={onCancel} className="btn btn-secondary">{cancelLabel}</button>
            </div>
        </div>
    )
}

export default TerminalOrRecordOnlyStep
