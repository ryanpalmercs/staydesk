import { useEffect, useState } from "react"
import { disconnectStripe, getConnectStatus } from "../api/stripeApi"
import { useSearchParams } from "react-router-dom"
import { updatePropertySetting, getPropertySettings } from "../api/settingsApi"
import { displayPrice, formatPrice, sanitizePrice } from "../utils/price"
import { displayPercent, formatPercent, parsePercent } from "../utils/percent"

function SettingsPage() {
    const [loading, setLoading] = useState(true)
    const [connected, setConnected] = useState(true)
    const [accountId, setAccountId] = useState(null)
    const [incidentalsHoldAmount, setIncidentalsHoldAmount] = useState('')
    const [lodgingTaxRate, setLodgingTaxRate] = useState('')
    const [propertySaving, setPropertySaving] = useState(null)
    const [incidentalsFocused, setIncidentalsFocused] = useState(false)
    const [taxFocused, setTaxFocused] = useState(false)
    const [searchParams] = useSearchParams()
    const error = searchParams.get('error')

    useEffect(() => {
        getStripeSettings()
        loadPropertySettings()
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

    async function loadPropertySettings() {
        const response = await getPropertySettings()
        const settings = response?.data
        setIncidentalsHoldAmount(formatPrice(settings?.find(s => s.name === 'incidentals_hold_amount')?.value))
        setLodgingTaxRate(formatPercent(settings?.find(s => s.name === 'lodging_tax_rate')?.value))
    }

    async function handlePropertySave(name, value) {
        setPropertySaving(name)
        const response = await updatePropertySetting(name, value)
        const updated = response?.data

        if (updated.name === 'incidentals_hold_amount') {
            setIncidentalsHoldAmount(updated.value)
        }

        if (updated.name === 'lodging_tax_rate') {
            setLodgingTaxRate(formatPercent(updated.value))
        }

        setPropertySaving(null)
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

            <div className="feat-card max-w-lg mt-6">
                <h3>Configurable Settings</h3>
                <div>
                    <label className="block text-sm text-muted mb-1">Incidentals Hold Amount ($)</label>
                    <div className="flex gap-2">
                        <input type="text" className="filter-input" value={incidentalsFocused ? incidentalsHoldAmount : displayPrice(incidentalsHoldAmount)} onChange={e => setIncidentalsHoldAmount(sanitizePrice(e.target.value))} onBlur={e => { setIncidentalsFocused(false) }} onFocus={() => setIncidentalsFocused(true)} />
                        <button className="btn-primary" disabled={propertySaving === 'incidentals_hold_amount'} onClick={() => handlePropertySave('incidentals_hold_amount', formatPrice(sanitizePrice(incidentalsHoldAmount)))}>{propertySaving === 'incidentals_hold_amount' ? 'Saving...' : 'Save'}</button>
                    </div>
                </div>
                <div className="mt-4">
                    <label className="block text-sm text-muted mb-1">Lodging Tax Rate</label>
                    <div className="flex gap-2">
                        <input type="text" step="0.01" className="filter-input" value={taxFocused ? lodgingTaxRate : displayPercent(lodgingTaxRate)} onChange={e => setLodgingTaxRate(e.target.value)} onBlur={e => { setTaxFocused(false) }} onFocus={() => setTaxFocused(true)} />
                        <button className="btn-primary" disabled={propertySaving === 'lodging_tax_rate'} onClick={() => handlePropertySave('lodging_tax_rate', parsePercent(lodgingTaxRate))}>{propertySaving === 'lodging_tax_rate' ? 'Saving...' : 'Save'}</button>
                    </div>
                </div>
            </div>
        </div>
    )
}

export default SettingsPage