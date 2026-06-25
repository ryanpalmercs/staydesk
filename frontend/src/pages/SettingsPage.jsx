import { useEffect, useRef, useState } from "react"
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
    const [incidentalsFocused, setIncidentalsFocused] = useState(false)
    const [taxFocused, setTaxFocused] = useState(false)
    const [searchParams] = useSearchParams()
    const [saving, setSaving] = useState(false)
    const [confirmationTemplate, setConfirmationTemplate] = useState('')
    const [checkInLinkTemplate, setCheckInLinkTemplate] = useState('')
    const [checkInCompleteTemplate, setCheckInCompleteTemplate] = useState('')
    const confirmationRef = useRef(null)
    const checkInLinkRef = useRef(null)
    const checkInCompleteRef = useRef(null)
    const error = searchParams.get('error')
    const originalSettings = useRef({})

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
        const incidentals = formatPrice(settings?.find(s => s.name === 'incidentals_hold_amount')?.value)
        const taxRate = formatPercent(settings?.find(s => s.name === 'lodging_tax_rate')?.value)
        const confirmation = settings?.find(s => s.name === 'sms_confirmation_template')?.value ?? ''
        const checkInLink = settings?.find(s => s.name === 'sms_checkin_link_template')?.value ?? ''
        const checkInComplete = settings?.find(s => s.name === 'sms_checkin_complete_template')?.value ?? ''

        setIncidentalsHoldAmount(incidentals)
        setLodgingTaxRate(taxRate)
        setConfirmationTemplate(confirmation)
        setCheckInLinkTemplate(checkInLink)
        setCheckInCompleteTemplate(checkInComplete)

        originalSettings.current = {
            incidentalsHoldAmount: incidentals,
            lodgingTaxRate: taxRate,
            confirmationTemplate: confirmation,
            checkInLinkTemplate: checkInLink,
            checkInCompleteTemplate: checkInComplete
        }
    }

    const isDirty = incidentalsHoldAmount !== originalSettings.current.incidentalsHoldAmount
        || lodgingTaxRate !== originalSettings.current.lodgingTaxRate
        || confirmationTemplate !== originalSettings.current.confirmationTemplate
        || checkInLinkTemplate !== originalSettings.current.checkInLinkTemplate
        || checkInCompleteTemplate !== originalSettings.current.checkInCompleteTemplate

    async function handleSave() {
        console.log('isDirty:', isDirty)
        console.log('state:', { incidentalsHoldAmount, lodgingTaxRate, confirmationTemplate, checkInLinkTemplate, checkInCompleteTemplate })
        console.log('original:', originalSettings.current)
        setSaving(true)
        const updates = []

        if (incidentalsHoldAmount !== originalSettings.current.incidentalsHoldAmount) {
            updates.push(updatePropertySetting('incidentals_hold_amount', formatPrice(sanitizePrice(incidentalsHoldAmount))))
        }

        if (lodgingTaxRate !== originalSettings.current.lodgingTaxRate) {
            updates.push(updatePropertySetting('lodging_tax_rate', parsePercent(lodgingTaxRate)))
        }

        if (confirmationTemplate !== originalSettings.current.confirmationTemplate) {
            updates.push(updatePropertySetting('sms_confirmation_template', confirmationTemplate))
        }

        if (checkInLinkTemplate !== originalSettings.current.checkInLinkTemplate) {
            updates.push(updatePropertySetting('sms_checkin_link_template', checkInLinkTemplate))
        }

        if (checkInCompleteTemplate !== originalSettings.current.checkInCompleteTemplate) {
            updates.push(updatePropertySetting('sms_checkin_complete_template', checkInCompleteTemplate))
        }

        const responses = await Promise.all(updates)

        responses.forEach(r => {
            const updated = r?.data
            switch (updated.name) {
                case 'incidentals_hold_amount':
                    setIncidentalsHoldAmount(formatPrice(updated.value))
                    originalSettings.current.incidentalsHoldAmount = formatPrice(updated.value)
                    break
                case 'lodging_tax_rate':
                    setLodgingTaxRate(formatPercent(updated.value))
                    originalSettings.current.lodgingTaxRate = formatPercent(updated.value)
                    break
                case 'sms_confirmation_template':
                    setConfirmationTemplate(updated.value)
                    originalSettings.current.confirmationTemplate = updated.value
                    break
                case 'sms_checkin_link_template':
                    setCheckInLinkTemplate(updated.value)
                    originalSettings.current.checkInLinkTemplate = updated.value
                    break
                case 'sms_checkin_complete_template':
                    setCheckInCompleteTemplate(updated.value)
                    originalSettings.current.checkInCompleteTemplate = updated.value
                    break
            }
        })

        setSaving(false)
    }

    function insertVariable(setter, ref, varName) {
        const el = ref.current
        const start = el.selectionStart
        const end = el.selectionEnd

        setter(prev => prev.slice(0, start) + `{{${varName}}}` + prev.slice(end))

        el.focus()
    }

    const CONFIRMATION_VARS = ['guestFirstName', 'guestLastName', 'checkInDate', 'checkOutDate', 'roomNumber', 'confirmationNumber']
    const CHECKIN_LINK_VARS = ['guestFirstName', 'guestLastName', 'checkInDate', 'link']
    const CHECKIN_COMPLETE_VARS = ['guestFirstName', 'guestLastName', 'roomNumber', 'doorCode']

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
                    <input type="text" className="filter-input" value={incidentalsFocused ? incidentalsHoldAmount : displayPrice(incidentalsHoldAmount)} onChange={e => setIncidentalsHoldAmount(sanitizePrice(e.target.value))} onBlur={() => setIncidentalsFocused(false)} onFocus={() => setIncidentalsFocused(true)} />
                </div>
                <div className="mt-4">
                    <label className="block text-sm text-muted mb-1">Lodging Tax Rate</label>
                    <input type="text" className="filter-input" value={taxFocused ? lodgingTaxRate : displayPercent(lodgingTaxRate)} onChange={e => setLodgingTaxRate(e.target.value)} onBlur={() => setTaxFocused(false)} onFocus={() => setTaxFocused(true)} />
                </div>
                <div className="mt-4">
                    <label className="block text-sm text-muted mb-1">Reservation Confirmation SMS</label>
                    <details>
                        <summary className="text-sm text-muted cursor-pointer mb-1">Insert variable</summary>
                        <div className="flex flex-wrap gap-1 mt-1 mb-2">
                            {CONFIRMATION_VARS.map(v => (
                                <button key={v} type="button" className="btn-chip" onClick={() => insertVariable(setConfirmationTemplate, confirmationRef, v)}>
                                    {`{{${v}}}`}
                                </button>
                            ))}
                        </div>
                    </details>
                    <textarea ref={confirmationRef} className="filter-input w-full" rows={3} value={confirmationTemplate} onChange={e => setConfirmationTemplate(e.target.value)} />
                </div>
                <div className="mt-4">
                    <label className="block text-sm text-muted mb-1">Remote Check-In Link SMS</label>
                    <details>
                        <summary className="text-sm text-muted cursor-pointer mb-1">Insert variable</summary>
                        <div className="flex flex-wrap gap-1 mt-1 mb-2">
                            {CHECKIN_LINK_VARS.map(v => (
                                <button key={v} type="button" className="btn-chip" onClick={() => insertVariable(setCheckInLinkTemplate, checkInLinkRef, v)}>
                                    {`{{${v}}}`}
                                </button>
                            ))}
                        </div>
                    </details>
                    <textarea ref={checkInLinkRef} className="filter-input w-full" rows={3} value={checkInLinkTemplate} onChange={e => setCheckInLinkTemplate(e.target.value)} />
                </div>
                <div className="mt-4">
                    <label className="block text-sm text-muted mb-1">Check-In Complete SMS</label>
                    <details>
                        <summary className="text-sm text-muted cursor-pointer mb-1">Insert variable</summary>
                        <div className="flex flex-wrap gap-1 mt-1 mb-2">
                            {CHECKIN_COMPLETE_VARS.map(v => (
                                <button key={v} type="button" className="btn-chip" onClick={() => insertVariable(setCheckInCompleteTemplate, checkInCompleteRef, v)}>
                                    {`{{${v}}}`}
                                </button>
                            ))}
                        </div>
                    </details>
                    <textarea ref={checkInCompleteRef} className="filter-input w-full" rows={3} value={checkInCompleteTemplate} onChange={e => setCheckInCompleteTemplate(e.target.value)} />
                </div>
                <button className="btn-primary mt-6" onClick={handleSave} disabled={saving || !isDirty}>
                    {saving ? 'Saving...' : 'Save'}
                </button>
            </div>
        </div>
    )
}

export default SettingsPage