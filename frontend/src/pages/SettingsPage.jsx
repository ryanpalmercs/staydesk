import { useEffect, useRef, useState } from "react"
import { connectSifely, disconnectSifely, getSifelyLocks, getSifelyStatus } from "../api/sifelyApi"
import { updatePropertySetting, getPropertySettings } from "../api/settingsApi"
import { getRoomTypes, updateRoomType } from "../api/roomTypeApi"
import { getRooms, updateRoom } from "../api/roomApi"
import { getRates, updateRate } from "../api/rateApi"
import { getRateOverrides, createRateOverride, deleteRateOverride } from "../api/rateOverrideApi"
import { displayPrice, formatPrice, sanitizePrice } from "../utils/price"
import { displayPercent, formatPercent, parsePercent } from "../utils/percent"
import { getPosDevices, pairPosDevice, unpairPosDevice } from "../api/posDeviceApi"
import { useAuth } from "../contexts/AuthContext"

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

function RoomLockRow({ room, locks, onChange }) {
    return (
        <div className="flex items-center gap-3">
            <span className="text-sm text-black flex-1">Room {room.roomNumber}</span>
            <select
                className="filter-input w-48"
                value={room.sifelyLockId ?? ''}
                onChange={e => onChange(room.id, e.target.value ? Number(e.target.value) : null)}
            >
                <option value="">Unassigned</option>
                {locks.map(lock => (
                    <option key={lock.lockId} value={lock.lockId}>
                        {lock.lockAlias || lock.lockName || lock.lockId}
                    </option>
                ))}
            </select>
        </div>
    )
}

function lastSurgedNight(endDate) {
    const d = new Date(endDate + 'T00:00:00')
    d.setDate(d.getDate() - 1)
    return d.toISOString().slice(0, 10)
}

function RateOverrideRow({ rateOverride, onDelete, canManage }) {
    return (
        <div className="flex items-center gap-3">
            <span className="text-sm text-black flex-1">
                {rateOverride.label} — {rateOverride.startDate} through {lastSurgedNight(rateOverride.endDate)} (back to normal {rateOverride.endDate}), {rateOverride.guestCount} guest{rateOverride.guestCount === 1 ? '' : 's'}
            </span>
            <span className="text-sm text-black w-24">{displayPrice(rateOverride.amount)}</span>
            {canManage && (
                <button type="button" className="text-sm font-medium text-muted hover:text-green" onClick={() => onDelete(rateOverride.id)}>
                    Delete
                </button>
            )}
        </div>
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
    const [rateOverrides, setRateOverrides] = useState([])
    const [overrideForm, setOverrideForm] = useState({ guestCount: '1', startDate: '', endDate: '', amount: '', label: '' })
    const [overrideCreating, setOverrideCreating] = useState(false)
    const [overrideError, setOverrideError] = useState(null)
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
    const { isSystemAdmin, role } = useAuth()
    const isAdmin = role === 'ADMIN'
    const [lockRooms, setLockRooms] = useState([])
    const [sifelyLocks, setSifelyLocks] = useState([])
    const [lockMappingLoading, setLockMappingLoading] = useState(true)
    const [lockMappingSaving, setLockMappingSaving] = useState(false)
    const originalLockRooms = useRef([])

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
        getRateOverrides().then(res => setRateOverrides(res.data ?? []))
    }, [])

    useEffect(() => {
        if (!isSystemAdmin) {
            return
        }

        setLockMappingLoading(true)
        Promise.all([getRooms(), getSifelyLocks()]).then(([roomsRes, locksRes]) => {
            const rooms = roomsRes.data ?? []
            setLockRooms(rooms)
            originalLockRooms.current = rooms
            setSifelyLocks(locksRes.data ?? [])
            setLockMappingLoading(false)
        })
    }, [isSystemAdmin])

    function handleLockRoomChange(roomId, sifelyLockId) {
        setLockRooms(prev => prev.map(r => r.id === roomId ? { ...r, sifelyLockId } : r))
    }

    const lockRoomsDirty = lockRooms.some(r =>
        r.sifelyLockId !== originalLockRooms.current.find(o => o.id === r.id)?.sifelyLockId)

    async function handleSaveLockRooms() {
        setLockMappingSaving(true)

        const dirty = lockRooms.filter(r =>
            r.sifelyLockId !== originalLockRooms.current.find(o => o.id === r.id)?.sifelyLockId)

        const responses = await Promise.all(dirty.map(r => updateRoom(r.id, { sifelyLockId: r.sifelyLockId })))
        const updated = responses.map(r => r.data)
        setLockRooms(prev => prev.map(r => updated.find(u => u.id === r.id) ?? r))
        originalLockRooms.current = originalLockRooms.current.map(o => updated.find(u => u.id === o.id) ?? o)

        setLockMappingSaving(false)
    }

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

    function handleOverrideFieldChange(e) {
        setOverrideForm({ ...overrideForm, [e.target.name]: e.target.value })
    }

    async function handleCreateOverride(e) {
        e.preventDefault()
        setOverrideError(null)
        setOverrideCreating(true)

        try {
            const res = await createRateOverride({
                rateType: 'NIGHTLY',
                guestCount: Number(overrideForm.guestCount),
                startDate: overrideForm.startDate,
                endDate: overrideForm.endDate,
                amount: sanitizePrice(overrideForm.amount),
                label: overrideForm.label
            })
            setRateOverrides(prev => [...prev, res.data])
            setOverrideForm({ guestCount: '1', startDate: '', endDate: '', amount: '', label: '' })
        } catch (err) {
            setOverrideError(err.response?.status === 409
                ? 'This date range overlaps an existing override for that guest count.'
                : 'Failed to save. Check the dates and amount.')
        }

        setOverrideCreating(false)
    }

    async function handleDeleteOverride(id) {
        await deleteRateOverride(id)
        setRateOverrides(prev => prev.filter(o => o.id !== id))
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

    const sortedLockRooms = [...lockRooms].sort((a, b) => a.roomNumber - b.roomNumber)

    const sortedRates = [...rates].sort((a, b) => {
        const typeDiff = RATE_TYPE_ORDER.indexOf(a.rateType) - RATE_TYPE_ORDER.indexOf(b.rateType)
        return typeDiff !== 0 ? typeDiff : a.guestCount - b.guestCount
    })

    const sortedRateOverrides = [...rateOverrides].sort((a, b) => a.startDate.localeCompare(b.startDate))

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

                {isSystemAdmin && (
                    <div className="feat-card">
                        <h3>Room Lock Mapping</h3>
                        <p>Assign each room to its Sifely door lock.</p>

                        {lockMappingLoading ? (
                            <p className="text-muted text-sm mt-4">Loading...</p>
                        ) : (
                            <>
                                <div className="flex flex-col gap-3 mt-4">
                                    {sortedLockRooms.map(room => (
                                        <RoomLockRow key={room.id} room={room} locks={sifelyLocks} onChange={handleLockRoomChange} />
                                    ))}
                                </div>
                                <button className="btn-primary mt-4" onClick={handleSaveLockRooms} disabled={!lockRoomsDirty || lockMappingSaving}>
                                    {lockMappingSaving ? 'Saving...' : 'Save'}
                                </button>
                            </>
                        )}
                    </div>
                )}

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

                <div className="feat-card lg:col-span-2">
                    <h3>Rate Overrides</h3>
                    <p>Set a different NIGHTLY rate for a date range — holidays, peak weekends, events.</p>

                    <div className="flex flex-col gap-3 mt-4">
                        {sortedRateOverrides.map(rateOverride => (
                            <RateOverrideRow key={rateOverride.id} rateOverride={rateOverride} onDelete={handleDeleteOverride} canManage={isAdmin} />
                        ))}
                        {sortedRateOverrides.length === 0 && <p className="text-muted text-sm">No overrides set.</p>}
                    </div>

                    {isAdmin && (
                        <form onSubmit={handleCreateOverride} className="grid grid-cols-1 sm:grid-cols-5 gap-2 mt-4 items-end">
                            <div>
                                <label className="block text-sm text-muted mb-1">Label</label>
                                <input name="label" value={overrideForm.label} onChange={handleOverrideFieldChange} className="filter-input w-full" required />
                            </div>
                            <div>
                                <label className="block text-sm text-muted mb-1">Guests</label>
                                <input type="number" name="guestCount" min="1" value={overrideForm.guestCount} onChange={handleOverrideFieldChange} className="filter-input w-full" required />
                            </div>
                            <div>
                                <label className="block text-sm text-muted mb-1">First surged night</label>
                                <input type="date" name="startDate" value={overrideForm.startDate} onChange={handleOverrideFieldChange} className="filter-input w-full" required />
                            </div>
                            <div>
                                <label className="block text-sm text-muted mb-1">Back to normal</label>
                                <input type="date" name="endDate" value={overrideForm.endDate} onChange={handleOverrideFieldChange} className="filter-input w-full" required />
                            </div>
                            <div>
                                <label className="block text-sm text-muted mb-1">Amount</label>
                                <input type="text" name="amount" value={overrideForm.amount} onChange={handleOverrideFieldChange} className="filter-input w-full" required />
                            </div>
                            <p className="text-xs text-muted sm:col-span-5 -mt-1">
                                "Back to normal" is the checkout-style date pricing reverts on — that night itself is not surged.
                            </p>
                            {overrideError && <p className="text-sm text-error sm:col-span-5">{overrideError}</p>}
                            <button type="submit" className="btn-primary sm:col-span-5 justify-self-start" disabled={overrideCreating}>
                                {overrideCreating ? 'Saving...' : 'Add Override'}
                            </button>
                        </form>
                    )}
                </div>

            </div>
        </div>
    )
}

export default SettingsPage