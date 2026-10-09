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
import { syncBacklogFolios } from "../api/reservationApi"
import { getQuickBooksStatus, startQuickBooksConnect, disconnectQuickBooks } from "../api/quickbooksApi"
import { getAllExtras, createExtra, updateExtra, deleteExtra } from "../api/extrasApi"
import { useAuth } from "../contexts/AuthContext"

const RATE_TYPE_LABELS = { NIGHTLY: 'Nightly', WEEKLY_5: 'Weekly (5-night)', WEEKLY_7: 'Weekly (7-night)' }
const RATE_TYPE_ORDER = ['NIGHTLY', 'WEEKLY_5', 'WEEKLY_7']
const BILLING_TYPE_LABELS = { FLAT: 'Flat', PER_NIGHT: 'Per night' }

function RoomTypeRow({ roomType, onChange }) {
    return (
        <div className="flex items-center gap-3">
            <input
                value={roomType.name}
                onChange={e => onChange(roomType.id, 'name', e.target.value)}
                className="filter-input flex-1"
            />
            <label className="flex items-center gap-2 text-sm text-black whitespace-nowrap">
                <input
                    type="checkbox"
                    checked={roomType.petFriendly}
                    onChange={e => onChange(roomType.id, 'petFriendly', e.target.checked)}
                />
                Pet friendly
            </label>
        </div>
    )
}

function AddOnRow({ addOn, onChange, onDelete }) {
    const [focused, setFocused] = useState(false)

    return (
        <div className="grid grid-cols-1 sm:grid-cols-[1.5fr_2fr_1fr_1fr_auto_auto_auto] items-center gap-2">
            <input
                value={addOn.name}
                onChange={e => onChange(addOn.id, { name: e.target.value })}
                placeholder="Name"
                className="filter-input"
            />
            <input
                value={addOn.description ?? ''}
                onChange={e => onChange(addOn.id, { description: e.target.value })}
                placeholder="Description (optional)"
                className="filter-input"
            />
            <input
                type="text"
                value={focused ? addOn.price : displayPrice(addOn.price)}
                onChange={e => onChange(addOn.id, { price: sanitizePrice(e.target.value) })}
                onFocus={() => setFocused(true)}
                onBlur={() => setFocused(false)}
                className="filter-input"
            />
            <select
                value={addOn.billingType}
                onChange={e => onChange(addOn.id, { billingType: e.target.value })}
                className="filter-input"
            >
                {Object.entries(BILLING_TYPE_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                ))}
            </select>
            <label className="flex items-center gap-2 text-sm text-black whitespace-nowrap">
                <input type="checkbox" checked={addOn.petFriendlyOnly} onChange={e => onChange(addOn.id, { petFriendlyOnly: e.target.checked })} />
                Pet rooms only
            </label>
            <label className="flex items-center gap-2 text-sm text-black whitespace-nowrap">
                <input type="checkbox" checked={addOn.active} onChange={e => onChange(addOn.id, { active: e.target.checked })} />
                Active
            </label>
            <button type="button" onClick={() => onDelete(addOn.id)} className="text-sm font-medium text-muted hover:text-error justify-self-start sm:justify-self-end">
                Delete
            </button>
        </div>
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
    const [qbLoading, setQbLoading] = useState(true)
    const [qbConnected, setQbConnected] = useState(false)
    const [qbRealmId, setQbRealmId] = useState(null)
    const [qbConnectedAt, setQbConnectedAt] = useState(null)
    const [qbConnecting, setQbConnecting] = useState(false)
    const [qbBanner, setQbBanner] = useState(null)
    const [incidentalsHoldAmount, setIncidentalsHoldAmount] = useState('')
    const [lodgingTaxRate, setLodgingTaxRate] = useState('')
    const [incidentalsFocused, setIncidentalsFocused] = useState(false)
    const [taxFocused, setTaxFocused] = useState(false)
    const [saving, setSaving] = useState(false)
    const [confirmationTemplate, setConfirmationTemplate] = useState('')
    const [checkInLinkTemplate, setCheckInLinkTemplate] = useState('')
    const [checkInCompleteTemplate, setCheckInCompleteTemplate] = useState('')
    const [emailConfirmationSubject, setEmailConfirmationSubject] = useState('')
    const [emailConfirmationBody, setEmailConfirmationBody] = useState('')
    const [emailCheckInLinkSubject, setEmailCheckInLinkSubject] = useState('')
    const [emailCheckInLinkBody, setEmailCheckInLinkBody] = useState('')
    const [emailCheckInCompleteSubject, setEmailCheckInCompleteSubject] = useState('')
    const [emailCheckInCompleteBody, setEmailCheckInCompleteBody] = useState('')
    const [roomTypes, setRoomTypes] = useState([])
    const [rates, setRates] = useState([])
    const [roomTypesSaving, setRoomTypesSaving] = useState(false)
    const [roomTypesError, setRoomTypesError] = useState(null)
    const [addOns, setAddOns] = useState([])
    const [addOnsSaving, setAddOnsSaving] = useState(false)
    const [addOnsError, setAddOnsError] = useState(null)
    const [newAddOnForm, setNewAddOnForm] = useState({ name: '', description: '', price: '', billingType: 'FLAT', petFriendlyOnly: false })
    const [addingAddOn, setAddingAddOn] = useState(false)
    const [ratesSaving, setRatesSaving] = useState(false)
    const [rateOverrides, setRateOverrides] = useState([])
    const [overrideForm, setOverrideForm] = useState({ guestCount: '1', startDate: '', endDate: '', amount: '', label: '' })
    const [overrideCreating, setOverrideCreating] = useState(false)
    const [overrideError, setOverrideError] = useState(null)
    const confirmationRef = useRef(null)
    const checkInLinkRef = useRef(null)
    const checkInCompleteRef = useRef(null)
    const emailConfirmationBodyRef = useRef(null)
    const emailCheckInLinkBodyRef = useRef(null)
    const emailCheckInCompleteBodyRef = useRef(null)
    const originalSettings = useRef({})
    const originalRoomTypes = useRef([])
    const originalRates = useRef([])
    const originalAddOns = useRef([])
    const [posDevices, setPosDevices] = useState([])
    const [pairForm, setPairForm] = useState({ pairingCode: '', friendlyName: '', location: '' })
    const [pairing, setPairing] = useState(false)
    const [pairError, setPairError] = useState(null)
    const [folioSyncing, setFolioSyncing] = useState(false)
    const [folioSyncResult, setFolioSyncResult] = useState(null)
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
        getRoomTypes(true).then(res => {
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
        getQuickBooksSettings()
        getAllExtras().then(res => {
            const data = res.data ?? []
            setAddOns(data)
            originalAddOns.current = data
        })
    }, [])

    useEffect(() => {
        const params = new URLSearchParams(window.location.search)
        const qbResult = params.get('quickbooks')

        if (qbResult === 'connected') {
            setQbBanner({ type: 'success', message: 'QuickBooks connected successfully.' })
        } else if (qbResult === 'error') {
            setQbBanner({ type: 'error', message: 'Failed to connect QuickBooks. Please try again.' })
        }

        if (qbResult) {
            params.delete('quickbooks')
            const newSearch = params.toString()
            window.history.replaceState({}, '', window.location.pathname + (newSearch ? `?${newSearch}` : ''))
        }
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

    function handleRoomTypeChange(id, field, value) {
        setRoomTypes(prev => prev.map(rt => rt.id === id ? { ...rt, [field]: value } : rt))
    }

    function roomTypeDirty(rt) {
        const original = originalRoomTypes.current.find(o => o.id === rt.id)
        return !original || rt.name !== original.name || rt.petFriendly !== original.petFriendly
    }

    function handleRateChange(id, amount) {
        setRates(prev => prev.map(r => r.id === id ? { ...r, amount } : r))
    }

    function handlePairFieldChange(e) {
        setPairForm({ ...pairForm, [e.target.name]: e.target.value })
    }

    const roomTypesDirty = roomTypes.some(roomTypeDirty)

    const ratesDirty = rates.some(r =>
        r.amount !== originalRates.current.find(o => o.id === r.id)?.amount)

    async function handleSaveRoomTypes() {
        setRoomTypesSaving(true)
        setRoomTypesError(null)

        const dirty = roomTypes.filter(roomTypeDirty)

        try {
            const responses = await Promise.all(dirty.map(rt => updateRoomType(rt.id, { name: rt.name, petFriendly: rt.petFriendly })))
            const updated = responses.map(r => r.data)
            setRoomTypes(prev => prev.map(rt => updated.find(u => u.id === rt.id) ?? rt))
            originalRoomTypes.current = originalRoomTypes.current.map(o => updated.find(u => u.id === o.id) ?? o)
        } catch (err) {
            setRoomTypesError(err.response?.status === 409 ? 'A room type with that name already exists.' : 'Failed to save.')
        }

        setRoomTypesSaving(false)
    }

    function handleAddOnChange(id, patch) {
        setAddOns(prev => prev.map(a => a.id === id ? { ...a, ...patch } : a))
    }

    function handleNewAddOnFieldChange(e) {
        const { name, value, type, checked } = e.target
        setNewAddOnForm(prev => ({ ...prev, [name]: type === 'checkbox' ? checked : value }))
    }

    function isAddOnDirty(addOn) {
        const original = originalAddOns.current.find(o => o.id === addOn.id)
        return !original || addOn.name !== original.name || addOn.description !== original.description
            || addOn.price !== original.price || addOn.billingType !== original.billingType
            || addOn.petFriendlyOnly !== original.petFriendlyOnly || addOn.active !== original.active
    }

    const addOnsDirty = addOns.some(isAddOnDirty)

    async function handleSaveAddOns() {
        setAddOnsSaving(true)
        setAddOnsError(null)

        const dirty = addOns.filter(isAddOnDirty)

        try {
            const responses = await Promise.all(dirty.map(a => updateExtra(a.id, {
                name: a.name, description: a.description, price: a.price, billingType: a.billingType,
                petFriendlyOnly: a.petFriendlyOnly, active: a.active
            })))
            const updated = responses.map(r => r.data)
            setAddOns(prev => prev.map(a => updated.find(u => u.id === a.id) ?? a))
            originalAddOns.current = originalAddOns.current.map(o => updated.find(u => u.id === o.id) ?? o)
        } catch {
            setAddOnsError('Failed to save.')
        }

        setAddOnsSaving(false)
    }

    async function handleCreateAddOn(e) {
        e.preventDefault()
        setAddingAddOn(true)
        setAddOnsError(null)

        try {
            const res = await createExtra({
                name: newAddOnForm.name, description: newAddOnForm.description,
                price: sanitizePrice(newAddOnForm.price), billingType: newAddOnForm.billingType,
                petFriendlyOnly: newAddOnForm.petFriendlyOnly
            })
            setAddOns(prev => [...prev, res.data])
            originalAddOns.current = [...originalAddOns.current, res.data]
            setNewAddOnForm({ name: '', description: '', price: '', billingType: 'FLAT', petFriendlyOnly: false })
        } catch {
            setAddOnsError('Failed to create add-on.')
        }

        setAddingAddOn(false)
    }

    async function handleDeleteAddOn(id) {
        await deleteExtra(id)
        setAddOns(prev => prev.filter(a => a.id !== id))
        originalAddOns.current = originalAddOns.current.filter(a => a.id !== id)
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

    async function handleSyncFolios() {
        setFolioSyncing(true)
        setFolioSyncResult(null)
        try {
            const res = await syncBacklogFolios()
            setFolioSyncResult(res.data)
        } catch (err) {
            setFolioSyncResult({ error: true })
        }
        setFolioSyncing(false)
    }

    const sortedLockRooms = [...lockRooms].sort((a, b) => a.roomNumber - b.roomNumber)

    const sortedRates = [...rates].sort((a, b) => {
        const typeDiff = RATE_TYPE_ORDER.indexOf(a.rateType) - RATE_TYPE_ORDER.indexOf(b.rateType)
        return typeDiff !== 0 ? typeDiff : a.guestCount - b.guestCount
    })

    const sortedRateOverrides = [...rateOverrides].sort((a, b) => a.startDate.localeCompare(b.startDate))

    async function getQuickBooksSettings() {
        setQbLoading(true)
        const response = await getQuickBooksStatus()
        setQbConnected(response?.data?.connected)
        setQbRealmId(response?.data?.realmId)
        setQbConnectedAt(response?.data?.connectedAt)
        setQbLoading(false)
    }

    async function handleQuickBooksConnect() {
        setQbConnecting(true)
        await startQuickBooksConnect()
    }

    async function handleQuickBooksDisconnect() {
        await disconnectQuickBooks()
        await getQuickBooksSettings()
    }

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
        const emailConfirmSubject = settings?.find(s => s.name === 'email_confirmation_subject')?.value ?? ''
        const emailConfirmBody = settings?.find(s => s.name === 'email_confirmation_body')?.value ?? ''
        const emailLinkSubject = settings?.find(s => s.name === 'email_checkin_link_subject')?.value ?? ''
        const emailLinkBody = settings?.find(s => s.name === 'email_checkin_link_body')?.value ?? ''
        const emailCompleteSubject = settings?.find(s => s.name === 'email_checkin_complete_subject')?.value ?? ''
        const emailCompleteBody = settings?.find(s => s.name === 'email_checkin_complete_body')?.value ?? ''

        setIncidentalsHoldAmount(incidentals)
        setLodgingTaxRate(taxRate)
        setConfirmationTemplate(confirmation)
        setCheckInLinkTemplate(checkInLink)
        setCheckInCompleteTemplate(checkInComplete)
        setEmailConfirmationSubject(emailConfirmSubject)
        setEmailConfirmationBody(emailConfirmBody)
        setEmailCheckInLinkSubject(emailLinkSubject)
        setEmailCheckInLinkBody(emailLinkBody)
        setEmailCheckInCompleteSubject(emailCompleteSubject)
        setEmailCheckInCompleteBody(emailCompleteBody)

        originalSettings.current = {
            incidentalsHoldAmount: incidentals,
            lodgingTaxRate: taxRate,
            confirmationTemplate: confirmation,
            checkInLinkTemplate: checkInLink,
            checkInCompleteTemplate: checkInComplete,
            emailConfirmationSubject: emailConfirmSubject,
            emailConfirmationBody: emailConfirmBody,
            emailCheckInLinkSubject: emailLinkSubject,
            emailCheckInLinkBody: emailLinkBody,
            emailCheckInCompleteSubject: emailCompleteSubject,
            emailCheckInCompleteBody: emailCompleteBody
        }
    }

    const isDirty = incidentalsHoldAmount !== originalSettings.current.incidentalsHoldAmount
        || lodgingTaxRate !== originalSettings.current.lodgingTaxRate
        || confirmationTemplate !== originalSettings.current.confirmationTemplate
        || checkInLinkTemplate !== originalSettings.current.checkInLinkTemplate
        || checkInCompleteTemplate !== originalSettings.current.checkInCompleteTemplate
        || emailConfirmationSubject !== originalSettings.current.emailConfirmationSubject
        || emailConfirmationBody !== originalSettings.current.emailConfirmationBody
        || emailCheckInLinkSubject !== originalSettings.current.emailCheckInLinkSubject
        || emailCheckInLinkBody !== originalSettings.current.emailCheckInLinkBody
        || emailCheckInCompleteSubject !== originalSettings.current.emailCheckInCompleteSubject
        || emailCheckInCompleteBody !== originalSettings.current.emailCheckInCompleteBody

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

        if (emailConfirmationSubject !== originalSettings.current.emailConfirmationSubject) {
            updates.push(updatePropertySetting('email_confirmation_subject', emailConfirmationSubject))
        }

        if (emailConfirmationBody !== originalSettings.current.emailConfirmationBody) {
            updates.push(updatePropertySetting('email_confirmation_body', emailConfirmationBody))
        }

        if (emailCheckInLinkSubject !== originalSettings.current.emailCheckInLinkSubject) {
            updates.push(updatePropertySetting('email_checkin_link_subject', emailCheckInLinkSubject))
        }

        if (emailCheckInLinkBody !== originalSettings.current.emailCheckInLinkBody) {
            updates.push(updatePropertySetting('email_checkin_link_body', emailCheckInLinkBody))
        }

        if (emailCheckInCompleteSubject !== originalSettings.current.emailCheckInCompleteSubject) {
            updates.push(updatePropertySetting('email_checkin_complete_subject', emailCheckInCompleteSubject))
        }

        if (emailCheckInCompleteBody !== originalSettings.current.emailCheckInCompleteBody) {
            updates.push(updatePropertySetting('email_checkin_complete_body', emailCheckInCompleteBody))
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
                case 'email_confirmation_subject':
                    setEmailConfirmationSubject(updated.value)
                    originalSettings.current.emailConfirmationSubject = updated.value
                    break
                case 'email_confirmation_body':
                    setEmailConfirmationBody(updated.value)
                    originalSettings.current.emailConfirmationBody = updated.value
                    break
                case 'email_checkin_link_subject':
                    setEmailCheckInLinkSubject(updated.value)
                    originalSettings.current.emailCheckInLinkSubject = updated.value
                    break
                case 'email_checkin_link_body':
                    setEmailCheckInLinkBody(updated.value)
                    originalSettings.current.emailCheckInLinkBody = updated.value
                    break
                case 'email_checkin_complete_subject':
                    setEmailCheckInCompleteSubject(updated.value)
                    originalSettings.current.emailCheckInCompleteSubject = updated.value
                    break
                case 'email_checkin_complete_body':
                    setEmailCheckInCompleteBody(updated.value)
                    originalSettings.current.emailCheckInCompleteBody = updated.value
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
                    <div className="mt-4">
                        <label className="block text-sm text-muted mb-1">Reservation Confirmation Email</label>
                        <input className="filter-input w-full mb-2" placeholder="Subject" value={emailConfirmationSubject} onChange={e => setEmailConfirmationSubject(e.target.value)} />
                        <details>
                            <summary className="text-sm text-muted cursor-pointer mb-1">Insert variable</summary>
                            <div className="flex flex-wrap gap-1 mt-1 mb-2">
                                {CONFIRMATION_VARS.map(v => (
                                    <button key={v} type="button" className="btn-chip" onClick={() => insertVariable(setEmailConfirmationBody, emailConfirmationBodyRef, v)}>
                                        {`{{${v}}}`}
                                    </button>
                                ))}
                            </div>
                        </details>
                        <textarea ref={emailConfirmationBodyRef} className="filter-input w-full" rows={4} value={emailConfirmationBody} onChange={e => setEmailConfirmationBody(e.target.value)} />
                    </div>
                    <div className="mt-4">
                        <label className="block text-sm text-muted mb-1">Remote Check-In Link Email</label>
                        <input className="filter-input w-full mb-2" placeholder="Subject" value={emailCheckInLinkSubject} onChange={e => setEmailCheckInLinkSubject(e.target.value)} />
                        <details>
                            <summary className="text-sm text-muted cursor-pointer mb-1">Insert variable</summary>
                            <div className="flex flex-wrap gap-1 mt-1 mb-2">
                                {CHECKIN_LINK_VARS.map(v => (
                                    <button key={v} type="button" className="btn-chip" onClick={() => insertVariable(setEmailCheckInLinkBody, emailCheckInLinkBodyRef, v)}>
                                        {`{{${v}}}`}
                                    </button>
                                ))}
                            </div>
                        </details>
                        <textarea ref={emailCheckInLinkBodyRef} className="filter-input w-full" rows={4} value={emailCheckInLinkBody} onChange={e => setEmailCheckInLinkBody(e.target.value)} />
                    </div>
                    <div className="mt-4">
                        <label className="block text-sm text-muted mb-1">Check-In Complete Email</label>
                        <input className="filter-input w-full mb-2" placeholder="Subject" value={emailCheckInCompleteSubject} onChange={e => setEmailCheckInCompleteSubject(e.target.value)} />
                        <details>
                            <summary className="text-sm text-muted cursor-pointer mb-1">Insert variable</summary>
                            <div className="flex flex-wrap gap-1 mt-1 mb-2">
                                {CHECKIN_COMPLETE_VARS.map(v => (
                                    <button key={v} type="button" className="btn-chip" onClick={() => insertVariable(setEmailCheckInCompleteBody, emailCheckInCompleteBodyRef, v)}>
                                        {`{{${v}}}`}
                                    </button>
                                ))}
                            </div>
                        </details>
                        <textarea ref={emailCheckInCompleteBodyRef} className="filter-input w-full" rows={4} value={emailCheckInCompleteBody} onChange={e => setEmailCheckInCompleteBody(e.target.value)} />
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
                    <h3>QuickBooks Payroll</h3>
                    <p>Connect QuickBooks Online to sync employee hours each pay period.</p>

                    {qbBanner && (
                        <p className={`text-sm mt-2 ${qbBanner.typee === 'success' ? 'text-green' : 'text-error'}`}>
                            {qbBanner.message}
                        </p>
                    )}

                    {qbLoading ? (
                        <p className="text-muted text-sm mt-4">Loading...</p>
                    ) : qbConnected ? (
                        <div className="mt-4">
                            <p className="text-sm text-muted mb-3">
                                Company ID: <span className="font-medium text-black">{qbRealmId}</span>
                            </p>
                            <p className="text-sm text-muted mb-3">
                                Connected: <span className="font-medium text-black">{new Date(qbConnectedAt).toLocaleString()}</span>
                            </p>
                            <button onClick={handleQuickBooksDisconnect} className="btn-secondary">Disconnect</button>
                        </div>
                    ) : (
                        <button onClick={handleQuickBooksConnect} className="btn-primary mt-4" disabled={qbConnecting}>
                            {qbConnecting ? 'Redirecting...' :'Connect QuickBooks'}
                        </button>
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

                <div className="feat-card lg:col-span-2">
                    <h3>Add-ons</h3>
                    <div className="flex flex-col gap-3 mt-4">
                        {addOns.map(addOn => (
                            <AddOnRow key={addOn.id} addOn={addOn} onChange={handleAddOnChange} onDelete={handleDeleteAddOn} />
                        ))}
                    </div>
                    {addOnsError && <p className="text-sm text-error mt-2">{addOnsError}</p>}
                    <button className="btn-primary mt-4" onClick={handleSaveAddOns} disabled={!addOnsDirty || addOnsSaving}>
                        {addOnsSaving ? 'Saving...' : 'Save'}
                    </button>
                    <form onSubmit={handleCreateAddOn} className="grid grid-cols-1 sm:grid-cols-[1.5fr_2fr_1fr_1fr_auto_auto] gap-2 mt-6 pt-6 border-t border-tan items-center">
                        <input name="name" placeholder="Name" value={newAddOnForm.name} onChange={handleNewAddOnFieldChange} className="filter-input" required />
                        <input name="description" placeholder="Description (optional)" value={newAddOnForm.description} onChange={handleNewAddOnFieldChange} className="filter-input" />
                        <input name="price" placeholder="Price" value={newAddOnForm.price} onChange={handleNewAddOnFieldChange} className="filter-input" required />
                        <select name="billingType" value={newAddOnForm.billingType} onChange={handleNewAddOnFieldChange} className="filter-input">
                            {Object.entries(BILLING_TYPE_LABELS).map(([value, label]) => (
                                <option key={value} value={value}>{label}</option>
                            ))}
                        </select>
                        <label className="flex items-center gap-2 text-sm text-black whitespace-nowrap">
                            <input type="checkbox" name="petFriendlyOnly" checked={newAddOnForm.petFriendlyOnly} onChange={handleNewAddOnFieldChange} />
                            Pet rooms only
                        </label>
                        <button type="submit" className="btn-primary" disabled={addingAddOn}>
                            {addingAddOn ? 'Adding...' : 'Add'}
                        </button>
                    </form>
                </div>

                <div className="feat-card">
                    <h3>Backlog Folio Sync</h3>
                    <p>Backlog check-ins are recorded without a folio charge, since staff already collected payment some other way. This posts the missing room charge to each one's folio (no payment is ever touched) — safe to run any time, reservations already caught up are left alone.</p>
                    <button className="btn-primary mt-4" onClick={handleSyncFolios} disabled={folioSyncing}>
                        {folioSyncing ? 'Syncing...' : 'Sync Folios'}
                    </button>
                    {folioSyncResult && (
                        folioSyncResult.error ? (
                            <p className="text-sm text-error mt-2">Sync failed. Try again.</p>
                        ) : (
                            <p className="text-sm text-muted mt-2">
                                {folioSyncResult.syncedCount === 0
                                    ? 'All folios are already in sync.'
                                    : `Synced ${folioSyncResult.syncedCount} folio${folioSyncResult.syncedCount === 1 ? '' : 's'}: ${folioSyncResult.confirmationCodes.join(', ')}`}
                            </p>
                        )
                    )}
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