import { useEffect, useState } from "react"
import { getPropertySetting } from "../api/settingsApi"
import { getPosDevices, getPosDeviceConfig } from '../api/posDeviceApi'
import AcceptJsCardForm from "./AcceptJsCardForm"
import { TerminalPaymentForm, RecordOnlyPaymentForm } from "./TerminalOrRecordOnlyPayment"

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
