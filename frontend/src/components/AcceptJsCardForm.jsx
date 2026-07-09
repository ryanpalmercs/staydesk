import { useEffect, useRef, useState } from "react"

const ACCEPT_JS_SRC = import.meta.env.VITE_AUTHORIZE_NET_ENVIRONMENT === 'PRODUCTION'
    ? 'https://js.authorize.net/v1/Accept.js'
    : 'https://jstest.authorize.net/v1/Accept.js'

function loadAcceptJs() {
    if (window.Accept) {
        return Promise.resolve()
    }

    return new Promise((resolve, reject) => {
        const script = document.createElement('script')
        script.src = ACCEPT_JS_SRC
        script.onload = resolve
        script.onerror = reject
        document.head.appendChild(script)
    })
}

function formatCardNumber(digits) {
    return digits.replace(/(.{4})/g, '$1 ').trim()
}

function formatExpiry(digits) {
    return digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits
}

function maskCardNumber(digits) {
    if (digits.length <= 4) {
        return formatCardNumber(digits)
    }
    return formatCardNumber('•'.repeat(digits.length - 4) + digits.slice(-4))
}

function detectBrand(digits) {
    if (/^4/.test(digits)) return 'visa'
    if (/^(5[1-5]|2(22[1-9]|2[3-9]\d|[3-6]\d\d|7[01]\d|720))/.test(digits)) return 'mastercard'
    if (/^3[47]/.test(digits)) return 'amex'
    if (/^(6011|65|64[4-9]|622)/.test(digits)) return 'discover'
    if (/^3(0[0-5]|[68])/.test(digits)) return 'diners'
    if (/^35(2[89]|[3-8]\d)/.test(digits)) return 'jcb'
    return null
}

function CardBrandIcon({ brand }) {
    const className = "w-8 h-5 flex-shrink-0"

    switch (brand) {
        case 'visa':
            return (
                <svg viewBox="0 0 32 20" className={className} aria-label="Visa">
                    <rect width="32" height="20" rx="3" fill="#1A1F71" />
                    <text x="16" y="14" textAnchor="middle" fontSize="9" fontStyle="italic" fontWeight="700" fill="#fff">VISA</text>
                </svg>
            )
        case 'mastercard':
            return (
                <svg viewBox="0 0 32 20" className={className} aria-label="Mastercard">
                    <rect width="32" height="20" rx="3" fill="#F0E0C8" />
                    <circle cx="13" cy="10" r="6" fill="#EB001B" />
                    <circle cx="19" cy="10" r="6" fill="#F79E1B" fillOpacity="0.85" />
                </svg>
            )
        case 'amex':
            return (
                <svg viewBox="0 0 32 20" className={className} aria-label="American Express">
                    <rect width="32" height="20" rx="3" fill="#2E77BC" />
                    <text x="16" y="14" textAnchor="middle" fontSize="7.5" fontWeight="700" fill="#fff">AMEX</text>
                </svg>
            )
        case 'discover':
            return (
                <svg viewBox="0 0 32 20" className={className} aria-label="Discover">
                    <rect width="32" height="20" rx="3" fill="#F0E0C8" />
                    <text x="12" y="14" textAnchor="middle" fontSize="6" fontWeight="700" fill="#2C1F14">DISC</text>
                    <circle cx="26" cy="10" r="5" fill="#F5A623" />
                </svg>
            )
        case 'diners':
            return (
                <svg viewBox="0 0 32 20" className={className} aria-label="Diners Club">
                    <rect width="32" height="20" rx="3" fill="#0079BE" />
                    <circle cx="16" cy="10" r="6" fill="#fff" />
                    <circle cx="16" cy="10" r="3.5" fill="#0079BE" />
                </svg>
            )
        case 'jcb':
            return (
                <svg viewBox="0 0 32 20" className={className} aria-label="JCB">
                    <rect width="32" height="20" rx="3" fill="#0B4EA2" />
                    <text x="16" y="14" textAnchor="middle" fontSize="8" fontWeight="700" fill="#fff">JCB</text>
                </svg>
            )
        default:
            return null
    }
}

function AcceptJsCardForm({ onCapture, onCancel, submitLabel = 'Confirm' }) {
    const [ready, setReady] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState(null)
    const [cardNumber, setCardNumber] = useState('')
    const [expiry, setExpiry] = useState('')
    const [cardCode, setCardCode] = useState('')
    const [cardNumberFocused, setCardNumberFocused] = useState(false)

    const expiryRef = useRef(null)
    const cvcRef = useRef(null)

    const cardDigits = cardNumber.replace(/\s/g, '')
    const brand = detectBrand(cardDigits)
    const cardNumberDisplay = cardNumberFocused ? cardNumber : maskCardNumber(cardDigits)

    useEffect(() => {
        loadAcceptJs().then(() => setReady(true)).catch(() => setError('Failed to load payment form.'))
    }, [])

    function handleSubmit(e) {
        e.preventDefault()
        setSubmitting(true)
        setError(null)

        const [month, year] = expiry.split('/')

        const secureData = {
            authData: {
                clientKey: import.meta.env.VITE_AUTHORIZE_NET_CLIENT_KEY,
                apiLoginID: import.meta.env.VITE_AUTHORIZE_NET_API_LOGIN_ID
            },
            cardData: {
                cardNumber: cardNumber.replace(/\s/g, ''),
                month,
                year,
                cardCode
            }
        }

        window.Accept.dispatchData(secureData, async response => {
            if (response.messages.resultCode !== 'Ok') {
                setError(response.messages.message[0]?.text ?? 'Card was declined.')
                setSubmitting(false)
                return
            }

            const token = JSON.stringify({
                dataDescriptor: response.opaqueData.dataDescriptor,
                dataValue: response.opaqueData.dataValue
            })

            try {
                await onCapture(token)
            } catch {
                setError('Failed to process card. Please try again.')
                setSubmitting(false)
            }
        })
    }

    return (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {/* No name attributes: Accept.js requires these fields never be part of a form POST (PCI SAQ-A-EP) */}
            <div className="filter-input flex items-center gap-2">
                {brand && <CardBrandIcon brand={brand} />}
                <input
                    placeholder="1234 1234 1234 1234"
                    value={cardNumberDisplay}
                    onChange={e => {
                        const digits = e.target.value.replace(/\D/g, '').slice(0, 16)
                        setCardNumber(formatCardNumber(digits))
                        if (digits.length === 16) {
                            expiryRef.current?.focus()
                        }
                    }}
                    onFocus={() => setCardNumberFocused(true)}
                    onBlur={() => setCardNumberFocused(false)}
                    inputMode="numeric"
                    autoComplete="off"
                    className="flex-1 min-w-0 outline-none bg-transparent text-sm"
                    required
                />
                <input
                    ref={expiryRef}
                    placeholder="MM/YY"
                    value={expiry}
                    onChange={e => {
                        const digits = e.target.value.replace(/\D/g, '').slice(0, 4)
                        setExpiry(formatExpiry(digits))
                        if (digits.length === 4) {
                            cvcRef.current?.focus()
                        }
                    }}
                    inputMode="numeric"
                    autoComplete="off"
                    className="w-14 outline-none bg-transparent text-sm"
                    required
                />
                <input
                    ref={cvcRef}
                    placeholder="CVC"
                    value={cardCode}
                    onChange={e => setCardCode(e.target.value.replace(/\D/g, '').slice(0, 4))}
                    inputMode="numeric"
                    autoComplete="off"
                    className="w-12 outline-none bg-transparent text-sm"
                    required
                />
            </div>

            {error && <p className="text-sm text-rust">{error}</p>}

            <div className="flex justify-end gap-3 mt-2">
                {onCancel && (
                    <button type="button" onClick={onCancel} className="btn btn-secondary" disabled={submitting}>
                        Back
                    </button>
                )}
                <button type="submit" className="btn btn-primary" disabled={!ready || submitting}>
                    {submitting ? 'Processing...' : submitLabel}
                </button>
            </div>
        </form>
    )
}

export default AcceptJsCardForm
