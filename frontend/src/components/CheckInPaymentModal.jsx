import { useEffect, useState } from "react"
import { CardElement, Elements, useElements, useStripe } from "@stripe/react-stripe-js"
import { loadStripe } from "@stripe/stripe-js"
import { getConnectStatus } from "../api/stripeApi"

function CheckInPaymentForm({ onConfirm, onClose }) {
    const stripe = useStripe()
    const elements = useElements()
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState(null)
    const [slowNotice, setSlowNotice] = useState(false)
    const [doorAccessFailed, setDoorAccessFailed] = useState(false)

    async function handleSubmit(e) {
        e.preventDefault()

        if (!stripe || !elements) {
            return
        }

        setSubmitting(true)
        setError(null)

        const card = elements.getElement(CardElement)

        const incidentalsResult = await stripe.createPaymentMethod({ type: 'card', card })
        if (incidentalsResult.error) {
            setError(incidentalsResult.error.message)
            setSubmitting(false)
            return
        }

        const slowTimer = setTimeout(() => setSlowNotice(true), 2000)

        try {
            const doorAccessStatus = await onConfirm(incidentalsResult.paymentMethod.id)
            if (doorAccessStatus === 'FAILED') {
                setDoorAccessFailed(true)
            } else {
                onClose()
            }
        } catch (err) {
            setError('Failed to check in. Please try a different card.')
        } finally {
            clearTimeout(slowTimer)
            setSubmitting(false)
            setSlowNotice(false)
        }
    }

    if (doorAccessFailed) {
        return (
            <div className="flex flex-col gap-4">
                <p className="text-sm text-rust font-medium">Door lock code couldn't be issued</p>
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

    return (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div className="filter-input">
                <CardElement options={{ style: { base: { fontSize: '16px' } } }} />
            </div>

            {error && <p className="text-sm text-rust">{error}</p>}

            <div className="flex justify-end gap-3 mt-2">
                <button type="button" onClick={onClose} className="btn btn-secondary" disabled={submitting}>
                    Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={!stripe || submitting}>
                    {submitting ? (slowNotice ? 'Still setting up door access...' : 'Checking in...') : 'Check In'}
                </button>
            </div>
        </form>
    )
}

function CheckInPaymentModal({ onConfirm, onClose }) {
    const [stripePromise, setStripePromise] = useState(null)
    const [error, setError] = useState(null)

    useEffect(() => {
        getConnectStatus().then(res => {
            if (res.data.connected) {
                setStripePromise(loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY, {
                    stripeAccount: res.data.accountId
                }))
            } else {
                setError('Stripe is not connected. Connect an account in Settings first.')
            }
        })
    }, [])

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-warm-white rounded-lg p-6 w-full max-w-md shadow-lg border-t-4 border-rust">
                <h2 className="text-lg text-charcoal font-semibold mb-4">Card for Incidentals</h2>
                <p className="text-sm text-muted mb-4">
                    We'll place a hold on this card as an incidentals buffer. It won't be charged unless needed at checkout.
                </p>
                {error && <p className="text-sm text-rust mb-4">{error}</p>}
                {stripePromise && (
                    <Elements stripe={stripePromise}>
                        <CheckInPaymentForm onConfirm={onConfirm} onClose={onClose} />
                    </Elements>
                )}
            </div>
        </div>
    )
}

export default CheckInPaymentModal