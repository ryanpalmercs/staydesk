import { useEffect, useState } from "react"
import { disconnectStripe, getConnectStatus } from "../api/stripeApi"
import { useSearchParams } from "react-router-dom"

function SettingsPage() {
    const [loading, setLoading] = useState(true)
    const [connected, setConnected] = useState(true)
    const [accountId, setAccountId] = useState(null)
    const [searchParams] = useSearchParams()
    const error = searchParams.get('error')

    useEffect(() => {
        getStripeSettings()
    }, [])

    async function getStripeSettings() {
        setLoading(true)
        const response = await getConnectStatus()
        setConnected(response?.data?.connected)
        setAccountId(response?.data?.accountId)
        setLoading(false)
    }

    async function handleDisconnect() {
        await disconnectStripe()
        await getStripeSettings()
    }

    return (
        <div>
            <h1 className="section-title">Settings</h1>

            {error === 'stripe_connect_failed' && (
                <p className="text-rust text-sm mb-6">Failed to connect Stripe account. Please try again.</p>
            )}

            <div className="feat-card max-w-lg">
                <h3>Stripe</h3>
                <p>Connect your Stripe account to enable payment processing.</p>

                {loading ? (
                    <p className="text-muted text-sm mt-4">Loading...</p>
                ) : connected ? (
                    <div className="mt-4">
                        <p className="text-sm text-muted mb-3">
                            Connected account: <span className="font-medium text-charcoal">{accountId}</span>
                        </p>
                        <button onClick={handleDisconnect} className="btn-secondary">Disconnect</button>
                    </div>
                ) : (
                    <div className="mt-4">
                        <a href={import.meta.env.VITE_API_BASE_URL + '/stripe/connect'} className="btn-primary">Connect Stripe Account</a>
                    </div>
                )}
            </div>
        </div>
    )
}

export default SettingsPage