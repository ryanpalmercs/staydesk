import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import AcceptJsCardForm from '../components/AcceptJsCardForm'
import { getRemoteCheckInSummary, completeRemoteCheckIn } from '../api/publicRemoteCheckInApi'

export default function RemoteCheckInPage() {
    const { token } = useParams()

    const [summary, setSummary] = useState(null)
    const [loadError, setLoadError] = useState(false)
    const [agreedToTerms, setAgreedToTerms] = useState(false)
    const [error, setError] = useState(null)
    const [done, setDone] = useState(false)

    useEffect(() => {
        getRemoteCheckInSummary(token)
            .then(res => setSummary(res.data))
            .catch(() => setLoadError(true))
    }, [token])

    async function handlePayment(paymentMethodId) {
        try {
            await completeRemoteCheckIn(token, paymentMethodId, agreedToTerms)
            setDone(true)
        } catch (err) {
            const message = typeof err.response?.data === 'string'
                ? err.response.data
                : 'We could not complete check-in. Please try again or call the front desk at (660) 258-7257.'
            throw new Error(message)
        }
    }

    if (loadError) {
        return (
            <div className="min-h-screen bg-black flex items-center justify-center px-6">
                <div className="bg-warm-white rounded-lg shadow-md p-8 max-w-sm text-center">
                    <h1 className="text-xl font-semibold text-black mb-2">This link isn't valid</h1>
                    <p className="text-black/70 text-sm mb-4">
                        This remote check-in link is invalid, expired, or has already been used.
                    </p>
                    <p className="text-sm">
                        Please call the front desk at <a href="tel:+16602587257" className="text-green underline">(660) 258-7257</a>.
                    </p>
                </div>
            </div>
        )
    }

    if (done) {
        return (
            <div className="min-h-screen bg-black flex items-center justify-center px-6">
                <div className="bg-warm-white rounded-lg shadow-md p-8 max-w-sm text-center">
                    <h1 className="text-xl font-semibold text-black mb-2">You're checked in!</h1>
                    <p className="text-black/70 text-sm">
                        We've texted and emailed your door code. See you soon at Martin House Motel.
                    </p>
                </div>
            </div>
        )
    }

    if (!summary) {
        return (
            <div className="min-h-screen bg-black flex items-center justify-center">
                <p className="text-cream/70">Loading...</p>
            </div>
        )
    }

    return (
        <div className="min-h-screen bg-black">
            <div className="max-w-md mx-auto px-6 py-12">
                <div className="mb-6 text-center">
                    <h1 className="welcome-title text-2xl mb-1 mx-auto">Remote Check-In</h1>
                    <p className="text-cream/60 text-sm">Hi {summary.guestFirstName}, let's get you checked in.</p>
                </div>

                <div className="bg-warm-white rounded-lg shadow-md p-6 space-y-5">
                    <div className="text-sm text-black/80 space-y-1">
                        <p><strong>Confirmation:</strong> {summary.confirmationCode}</p>
                        <p><strong>Room type:</strong> {summary.roomTypeName}</p>
                        <p><strong>Dates:</strong> {summary.checkInDate} &rarr; {summary.checkOutDate}</p>
                        <p><strong>Guests:</strong> {summary.guestCount}</p>
                    </div>

                    <label className="flex items-start gap-2 text-sm text-black/80">
                        <input type="checkbox" checked={agreedToTerms} onChange={e => setAgreedToTerms(e.target.checked)} className="mt-1" />
                        <span>
                            I agree to the <a href="/terms" target="_blank" rel="noopener noreferrer" className="text-green underline">Terms of Service</a>,
                            including the cancellation policy and house rules, and authorize an incidental hold on my card.
                        </span>
                    </label>

                    {error && <p className="text-error text-sm">{error}</p>}

                    {agreedToTerms ? (
                        <AcceptJsCardForm onCapture={handlePayment} submitLabel="Complete check-in" />
                    ) : (
                        <p className="text-sm text-muted">Please agree to the terms above to continue.</p>
                    )}
                </div>
            </div>
        </div>
    )
}
