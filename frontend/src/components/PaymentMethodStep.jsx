import { useEffect, useState } from "react"
import { getPropertySetting } from "../api/settingsApi"
import { getPosDevices, getPosDeviceConfig, checkPosDeviceHealth } from '../api/posDeviceApi'
import { displayPrice } from "../utils/price"
import AcceptJsCardForm from "./AcceptJsCardForm"

function AmountBanner({ amount, label }) {
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

function AcceptJsPaymentForm({ onSubmitToken, onCancel, dual, submitLabel, amount, amountLabel }) {
    return (
        <AcceptJsCardForm
            onCapture={onSubmitToken}
            onCancel={onCancel}
            submitLabel={submitLabel}
            dual={dual}
            amount={amount}
            label={amountLabel}
        />
    )
}

function TerminalPaymentForm({ onSubmitTerminal, onCancel, devices, onHealthCheck, amount, amountLabel, errorMessage }) {
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
                onHealthCheck(res.data.online)
            })
            .catch(() => {
                if (cancelled) {
                    return
                }

                setDeviceOnline(false)
                onHealthCheck(false)
            })

        return () => { cancelled = true }
    }, [selectedDeviceId])

    async function handleSubmit(e) {
        e.preventDefault()
        setSubmitting(true)
        setError(true)

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
                    Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting || !selectedDeviceId || deviceOnline === false}>
                    {submitting ? 'Waiting on terminal...' : 'Charge on Terminal'}
                </button>
            </div>
        </form>
    )
}

function RecordOnlyPaymentForm({ onSubmitTerminal, onCancel, amount, amountLabel, errorMessage }) {
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
                    Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                    {submitting ? 'Recording...' : 'Record Charge (No Terminal)'}
                </button>
            </div>
        </form>
    )
}

function PaymentMethodStep({ amount, amountLabel = 'Amount', description, dual = false, submitLabel = 'Confirm', onSubmitToken, onSubmitTerminal, onCancel, terminalErrorMessage = 'Payment failed. The card may have been declined on the terminal.', recordOnlyErrorMessage = 'Payment failed.', }) {
    const [provider, setProvider] = useState(null)
    const [posDevices, setPosDevices] = useState([])
    const [cardPresentRecordOnly, setCardPresentRecordOnly] = useState(false)
    const [useTerminal, setUseTerminal] = useState(false)
    const [manualEntryUnlocked, setManualEntryUnlocked] = useState(false)
    const [error, setError] = useState(null)

    useEffect(() => {
        getPropertySetting('payment_provider').then(res => {
            setProvider(res.data.value)
        })

        getPosDevices().then(res => {
            const devices = res.data ?? []
            setPosDevices(devices)
            setUseTerminal(devices.length > 0)
        })

        getPosDeviceConfig().then(res => setCardPresentRecordOnly(res.data.recordOnly))
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

    const hasManualProvider = provider === 'authorizenet'
    const noDeviceRecordOnly = posDevices.length === 0 && cardPresentRecordOnly

    return (
        <>
            {description && <p className="text-sm text-muted mb-4">{description}</p>}
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
                <TerminalPaymentForm
                    onSubmitTerminal={onSubmitTerminal}
                    onCancel={onCancel}
                    devices={posDevices}
                    onHealthCheck={handleHealthCheck}
                    amount={amount}
                    amountLabel={amountLabel}
                    errorMessage={terminalErrorMessage}
                />
            )}
            {noDeviceRecordOnly && (
                <RecordOnlyPaymentForm
                    onSubmitTerminal={onSubmitTerminal}
                    onCancel={onCancel}
                    amount={amount}
                    amountLabel={amountLabel}
                    errorMessage={recordOnlyErrorMessage}
                />
            )}
            {!useTerminal && !noDeviceRecordOnly && provider === 'authorizenet' && (
                <AcceptJsPaymentForm
                    onSubmitToken={onSubmitToken}
                    onCancel={onCancel}
                    dual={dual}
                    submitLabel={submitLabel}
                    amount={amount}
                    amountLabel={amountLabel}
                />
            )}
            {!useTerminal && posDevices.length > 0 && !hasManualProvider && (
                <p className="text-sm text-error">Terminal unavailable and no backup card entry is configured. Contact support.</p>
            )}
        </>
    )
}

export default PaymentMethodStep