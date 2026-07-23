import { useEffect, useRef, useState } from "react"
import { connectSifely, disconnectSifely, getSifelyStatus } from "../api/sifelyApi"
import { updatePropertySetting, getPropertySettings } from "../api/settingsApi"
import { getRoomTypes, updateRoomType } from "../api/roomTypeApi"
import { getRates, updateRate } from "../api/rateApi"
import { displayPrice, formatPrice, sanitizePrice } from "../utils/price"
import { displayPercent, formatPercent, parsePercent } from "../utils/percent"
import { getPosDevices, pairPosDevice, unpairPosDevice } from "../api/posDeviceApi"

const RATE_TYPE_LABELS = { NIGHTLY: 'Nightly', WEEKLY_5: 'Weekly (5-night)', WEEKLY_7: 'Weekly (7-night)' }
const RATE_TYPE_ORDER = ['NIGHTLY', 'WEEKLY_5', 'WEEKLY_7']

function RoomTypeRow({ roomType, onChange }) {
    return (
        <input
            value={roomType.name}
            onChange={e => onChange(roomType.id, e.target.value)}
            className="filter-input w-full"
        />
    )
}

function RateRow({ rate, onChange }) {
    const [focused, setFocused] = useState(false)

    return (
        <div className="flex items-center gap-3">
            <span className="text-sm text-black flex-1">
                {RATE_TYPE_LABELS[rate.rateType] ?? rate.rateType} — {rate.guestCount} guest{rate.guestCount === 1 ? '' : 's'}
            </span>
            <input
                type="text"
                className="filter-input w-32"
                value={focused ? rate.amount : displayPrice(rate.amount)}
                onChange={e => onChange(rate.id, sanitizePrice(e.target.value))}
                onFocus={() => setFocused(true)}
                onBlur={() => setFocused(false)}
            />
        </div>
    )
}

function SettingsPage() {
    const [sifelyLoading, setSifelyLoading] = useState(true)
    const [sifelyConnected, setSifelyConnected] = useState(false)
    const [sifelyClientId, setSifelyClientId] = useState(null)
    const [sifelyConnectedAt, setSifelyConnectedAt] = useState(null)
    const [sifelyForm, setSifelyForm] = useState({ account: '', password: '', clientId: '', clientSecret: '' })
    const [sifelyError, setSifelyError] = useState(null)
    const [sifelyConnecting, setSifelyConnecting] = useState(false)
    const [incidentalsHoldAmount, setIncidentalsHoldAmount] = useState('')
    const [lodgingTaxRate, setLodgingTaxRate] = useState('')
    const [incidentalsFocused, setIncidentalsFocused] = useState(false)
    const [taxFocused, setTaxFocused] = useState(false)
    const [saving, setSaving] = useState(false)
    const [confirmationTemplate, setConfirmationTemplate] = useState('')
    const [checkInLinkTemplate, setCheckInLinkTemplate] = useState('')
    const [checkInCompleteTemplate, setCheckInCompleteTemplate] = useState('')
    const [roomTypes, setRoomTypes] = useState([])
    const [rates, setRates] = useState([])
    const [roomTypesSaving, setRoomTypesSaving] = useState(false)
    const [roomTypesError, setRoomTypesError] = useState(null)
    const [ratesSaving, setRatesSaving] = useState(false)
    const confirmationRef = useRef(null)
    const checkInLinkRef = useRef(null)
    const checkInCompleteRef = useRef(null)
    const originalSettings = useRef({})
    const originalRoomTypes = useRef([])
    const originalRates = useRef([])
    const [posDevices, setPosDevices] = useState([])
    const [pairForm, setPairForm] = useState({ pairingCode: '', friendlyName: '', location: '' })
    const [pairing, setPairing] = useState(false)
    const [pairError, setPairError] = useState(null)

    useEffect(() => {
        getSifelySettings()
        loadPropertySettings()
        getRoomTypes().then(res => {
            const data = res.data ?? []
            setRoomTypes(data)
            originalRoomTypes.current = data
        })
        getRates().then(res => {
            const data = (res.data ?? []).map(r => ({ ...r, amount: formatPrice(r.amount) }))
            setRates(data)
            originalRates.current = data
        })
        getPosDevices().then(res => setPosDevices(res.data ?? []))
    }, [])

    function handleRoomTypeChange(id, name) {
        setRoomTypes(prev => prev.map(rt => rt.id === id ? { ...rt, name } : rt))
    }

    function handleRateChange(id, amount) {
        setRates(prev => prev.map(r => r.id === id ? { ...r, amount } : r))
    }

    function handlePairFieldChange(e) {
        setPairForm({ ...pairForm, [e.target.name]: e.target.value })
    }

    const roomTypesDirty = roomTypes.some(rt =>
        rt.name !== originalRoomTypes.current.find(o => o.id === rt.id)?.name)

    const ratesDirty = rates.some(r =>
        r.amount !== originalRates.current.find(o => o.id === r.id)?.amount)

    async function handleSaveRoomTypes() {
        setRoomTypesSaving(true)
        setRoomTypesError(null)

        const dirty = roomTypes.filter(rt =>
            rt.name !== originalRoomTypes.current.find(o => o.id === rt.id)?.name)

        try {
            const responses = await Promise.all(dirty.map(rt => updateRoomType(rt.id, { name: rt.name })))
            const updated = responses.map(r => r.data)
            setRoomTypes(prev => prev.map(rt => updated.find(u => u.id === rt.id) ?? rt))
            originalRoomTypes.current = originalRoomTypes.current.map(o => updated.find(u => u.id === o.id) ?? o)
        } catch (err) {
            setRoomTypesError(err.response?.status === 409 ? 'A room type with that name already exists.' : 'Failed to save.')
        }

        setRoomTypesSaving(false)
    }

    async function handleSaveRates() {
        setRatesSaving(true)

        const dirty = rates.filter(r =>
            r.amount !== originalRates.current.find(o => o.id === r.id)?.amount)

        const responses = await Promise.all(dirty.map(r => updateRate(r.id, { ...r, amount: sanitizePrice(r.amount) })))
        const updated = responses.map(r => ({ ...r.data, amount: formatPrice(r.data.amount) }))
        setRates(prev => prev.map(r => updated.find(u => u.id === r.id) ?? r))
        originalRates.current = originalRates.current.map(o => updated.find(u => u.id === o.id) ?? o)

        setRatesSaving(false)
    }

    async function handlePairDevice(e) {
        e.preventDefault()
        setPairError(null)
        setPairing(true)
        try {
            const res = await pairPosDevice(pairForm)
            setPosDevices(prev => [...prev, res.data])
            setPairForm({ pairingCode: '', friendlyName: '', location: '' })
        } catch (err) {
            setPairError('Failed to pair device — check the pairing code shown on the terminal.')
        }
        setPairing(false)
    }

    async function handleUnpairDevice(id) {
        await unpairPosDevice(id)
        setPosDevices(prev => prev.filter(d => d.id !== id))
    }

    const sortedRates = [...rates].sort((a, b) => {
        const typeDiff = RATE_TYPE_ORDER.indexOf(a.rateType) - RATE_TYPE_ORDER.indexOf(b.rateType)
        return typeDiff !== 0 ? typeDiff : a.guestCount - b.guestCount
    })

    async function getSifelySettings() {
        setSifelyLoading(true)
        const response = await getSifelyStatus()
        setSifelyConnected(response?.data?.connected)
        setSifelyClientId(response?.data?.clientId)
        setSifelyConnectedAt(response?.data?.connectedAt)
        setSifelyLoading(false)
    }

    function handleSifelyFieldChange(e) {
        setSifelyForm({ ...sifelyForm, [e.target.name]: e.target.value })
    }

    async function handleSifelyConnect(e) {
        e.preventDefault()
        setSifelyError(null)
        setSifelyConnecting(true)

        try {
            await connectSifely(sifelyForm)
            setSifelyForm({ account: '', password: '', clientId: '', clientSecret: '' })
            await getSifelySettings()
        } catch {
            setSifelyError('Failed to connect Sifely account. Check your credentials and try again.')
        } finally {
            setSifelyConnecting(false)
        }
    }

    async function handleSifelyDisconnect() {
        await disconnectSifely()
        await getSifelySettings()
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

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">

                <div className="feat-card lg:col-span-2">
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

                <div className="feat-card">
                    <h3>Sifely Smart Lock</h3>
                    <p>Connect your Sifely account to enable electronic door lock access.</p>

                    {sifelyLoading ? (
                        <p className="text-muted text-sm mt-4">Loading...</p>
                    ) : sifelyConnected ? (
                        <div className="mt-4">
                            <p className="text-sm text-muted mb-3">
                                Client ID: <span className="font-medium text-black">{sifelyClientId}</span>
                            </p>
                            <p className="text-sm text-muted mb-3">
                                Connected: <span className="font-medium text-black">{new Date(sifelyConnectedAt).toLocaleString()}</span>
                            </p>
                            <button onClick={handleSifelyDisconnect} className="btn-secondary">Disconnect</button>
                        </div>
                    ) : (
                        <form onSubmit={handleSifelyConnect} className="flex flex-col gap-3 mt-4">
                            <div>
                                <label className="block text-sm text-muted mb-1">Account</label>
                                <input name="account" value={sifelyForm.account} onChange={handleSifelyFieldChange} className="filter-input" required />
                            </div>
                            <div>
                                <label className="block text-sm text-muted mb-1">Password</label>
                                <input type="password" name="password" value={sifelyForm.password} onChange={handleSifelyFieldChange} className="filter-input" required />
                            </div>
                            <div>
                                <label className="block text-sm text-muted mb-1">Client ID</label>
                                <input name="clientId" value={sifelyForm.clientId} onChange={handleSifelyFieldChange} className="filter-input" required />
                            </div>
                            <div>
                                <label className="block text-sm text-muted mb-1">Client Secret</label>
                                <input type="password" name="clientSecret" value={sifelyForm.clientSecret} onChange={handleSifelyFieldChange} className="filter-input" required />
                            </div>
                            {sifelyError && <p className="text-sm text-error">{sifelyError}</p>}
                            <button type="submit" className="btn-primary self-start" disabled={sifelyConnecting}>
                                {sifelyConnecting ? 'Connecting...' : 'Connect Sifely Account'}
                            </button>
                        </form>
                    )}
                </div>

                <div className="feat-card">
                    <h3>Room Types</h3>
                    <div className="flex flex-col gap-3 mt-4">
                        {roomTypes.map(roomType => (
                            <RoomTypeRow key={roomType.id} roomType={roomType} onChange={handleRoomTypeChange} />
                        ))}
                    </div>
                    {roomTypesError && <p className="text-sm text-error mt-2">{roomTypesError}</p>}
                    <button className="btn-primary mt-4" onClick={handleSaveRoomTypes} disabled={!roomTypesDirty || roomTypesSaving}>
                        {roomTypesSaving ? 'Saving...' : 'Save'}
                    </button>
                </div>

                <div className="feat-card">
                    <h3>Terminals</h3>
                    <p>Pair a card-present terminal using the pairing code shown on its screen.</p>

                    <form onSubmit={handlePairDevice} className="grid grid-cols-1 sm:grid-cols-3 gap-2 mt-4">
                        <input name="pairingCode" placeholder="Pairing code" value={pairForm.pairingCode} onChange={handlePairFieldChange} className="filter-input" required />
                        <input name="friendlyName" placeholder="Name (max 12 chars)" maxLength={12} value={pairForm.friendlyName} onChange={handlePairFieldChange} className="filter-input" required />
                        <input name="location" placeholder="Location (optional)" maxLength={16} value={pairForm.location} onChange={handlePairFieldChange} className="filter-input" />
                    </form>
                    {pairError && <p className="text-sm text-error mt-2">{pairError}</p>}
                    <button onClick={handlePairDevice} className="btn-primary mt-2" disabled={pairing}>
                        {pairing ? 'Pairing...' : 'Pair Terminal'}
                    </button>

                    <div className="flex flex-col gap-2 mt-4">
                        {posDevices.map(device => (
                            <div key={device.id} className="flex items-center justify-between">
                                <span className="text-sm text-black">{device.friendlyName}{device.location ? ` — ${device.location}` : ''}</span>
                                <button onClick={() => handleUnpairDevice(device.id)} className="text-sm font-medium text-muted hover:text-green">Unpair</button>
                            </div>
                        ))}
                    </div>
                </div>

                <div className="feat-card lg:col-span-2">
                    <h3>Rates</h3>
                    <div className="flex flex-col gap-3 mt-4">
                        {sortedRates.map(rate => (
                            <RateRow key={rate.id} rate={rate} onChange={handleRateChange} />
                        ))}
                    </div>
                    <button className="btn-primary mt-4" onClick={handleSaveRates} disabled={!ratesDirty || ratesSaving}>
                        {ratesSaving ? 'Saving...' : 'Save'}
                    </button>
                </div>

            </div>
        </div>
    )
}

export default SettingsPage