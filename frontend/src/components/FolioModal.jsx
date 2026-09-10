import { useEffect, useState } from "react"
import {
    getFolio, getFolioItems, addFolioItem, payFolio, getFolioIncidentCharges, getFolioPayments,
    chargeExtra, chargeExtraTerminal
} from "../api/folioApi"
import { getExtras } from "../api/extrasApi"
import { getPosDevices, getPosDeviceConfig } from "../api/posDeviceApi"
import Modal from "./Modal"
import StatusBadge from "./StatusBadge"
import IncidentChargeRequestModal from "./IncidentChargeRequestModal"

function FolioModal({ folioId, onClose, onPaid }) {
    const [folio, setFolio] = useState(null)
    const [items, setItems] = useState([])
    const [extras, setExtras] = useState([])
    const [payments, setPayments] = useState([])
    const [incidentCharges, setIncidentCharges] = useState([])
    const [selectedExtraId, setSelectedExtraId] = useState('')
    const [quantity, setQuantity] = useState(1)
    const [paying, setPaying] = useState(false)
    const [error, setError] = useState(null)
    const [showIncidentChargeModal, setShowIncidentChargeModal] = useState(false)
    const [posDevices, setPosDevices] = useState([])
    const [cardPresentRecordOnly, setCardPresentRecordOnly] = useState(false)
    const [selectedDeviceId, setSelectedDeviceId] = useState('')
    const [chargePrompt, setChargePrompt] = useState(null)
    const [chargingExtra, setChargingExtra] = useState(false)
    const [chargeError, setChargeError] = useState(null)
    const failedPayments = payments.filter(p => p.status === 'FAILED')

    useEffect(() => {
        loadFolio()
        getExtras().then(res => setExtras(res.data))
        getPosDevices().then(res => setPosDevices(res.data ?? []))
        getPosDeviceConfig().then(res => setCardPresentRecordOnly(res.data.recordOnly))
    }, [folioId])

    useEffect(() => {
        if (posDevices.length > 0) setSelectedDeviceId(String(posDevices[0].id))
    }, [posDevices])

    async function loadFolio() {
        const [folioRes, itemsRes, paymentsRes] = await Promise.all([getFolio(folioId), getFolioItems(folioId), getFolioPayments(folioId)])
        setFolio(folioRes.data)
        setItems(itemsRes.data ?? [])
        setPayments(paymentsRes.data ?? [])

        if (folioRes.data.status === 'CLOSED') {
            const chargesRes = await getFolioIncidentCharges(folioId)
            setIncidentCharges(chargesRes.data ?? [])
        }

        return folioRes.data
    }

    async function handleAddExtra() {
        if (!selectedExtraId) return

        setError(null)
        const extra = extras.find(e => e.id === Number(selectedExtraId))
        const totalBefore = folio.total

        try {
            await addFolioItem(folioId, Number(selectedExtraId), Number(quantity))
            setSelectedExtraId('')
            setQuantity(1)
            const updated = await loadFolio()
            const chargeAmount = Math.round((updated.total - totalBefore) * 100) / 100

            if (chargeAmount > 0) {
                await attemptChargeExtra(chargeAmount, extra?.name ?? 'Extra')
            }
        } catch (err) {
            setError(err.response?.status === 409 ? 'Folio is closed.' : 'Failed to add item.')
        }
    }

    async function attemptChargeExtra(amount, description) {
        setChargingExtra(true)
        setChargeError(null)

        try {
            await chargeExtra(folioId, amount, description)
            await loadFolio()
        } catch (err) {
            if (err.response?.status === 409) {
                setChargePrompt({ amount, description })
            } else {
                setChargeError(`Failed to charge ${description}.`)
            }
        }

        setChargingExtra(false)
    }

    async function handleChargeExtraTerminal(posDeviceId) {
        setChargingExtra(true)
        setChargeError(null)

        try {
            await chargeExtraTerminal(folioId, chargePrompt.amount, chargePrompt.description, posDeviceId)
            setChargePrompt(null)
            await loadFolio()
        } catch (err) {
            setChargeError('Failed to charge on terminal.')
        }

        setChargingExtra(false)
    }

    async function handlePay() {
        setPaying(true)
        setError(null)
        try {
            await payFolio(folioId)
            onPaid?.()
            onClose()
        } catch (err) {
            setError('Payment capture failed.')
            setPaying(false)
            loadFolio()
        }
    }

    function handleIncidentChargeRequested() {
        setShowIncidentChargeModal(false)
        loadFolio()
    }

    if (!folio) return null

    return (
        <Modal onClose={onClose} size="lg">
            <h2 className="text-lg text-black font-semibold mb-4">Folio</h2>

            <table className="w-full text-sm mb-4">
                <tbody>
                    {items.map(item => (
                        <tr key={item.id} className="border-b border-tan">
                            <td className="py-2">{item.description}</td>
                            <td className="py-2 text-right">${item.amount.toFixed(2)}</td>
                        </tr>
                    ))}
                    <tr className="font-semibold">
                        <td className="py-2">Total</td>
                        <td className="py-2 text-right">${folio.total.toFixed(2)}</td>
                    </tr>
                </tbody>
            </table>

            {folio.status === 'OPEN' && (
                <div className="flex gap-2 items-end mb-4">
                    <select value={selectedExtraId} onChange={e => setSelectedExtraId(e.target.value)} className="filter-input flex-1">
                        <option value="">Add an extra...</option>
                        {extras.map(extra => (
                            <option key={extra.id} value={extra.id}>
                                {extra.name} (${extra.price.toFixed(2)}{extra.billingType === 'PER_NIGHT' ? '/night' : ''})
                            </option>
                        ))}
                    </select>
                    <input type="number" min="1" value={quantity} onChange={e => setQuantity(e.target.value)} className="filter-input w-20" />
                    <button onClick={handleAddExtra} className="btn btn-secondary" disabled={chargingExtra}>Add</button>
                </div>
            )}

            {chargePrompt && (
                <div className="mb-4 p-3 rounded border border-tan">
                    <div className="flex justify-between items-baseline mb-2">
                        <span className="text-sm text-muted">Charge {chargePrompt.description} on terminal</span>
                        <span className="text-lg font-semibold text-black">${chargePrompt.amount.toFixed(2)}</span>
                    </div>
                    <p className="text-sm text-muted mb-2">
                        No card on file for this amount — collect it now so the folio stays accurate.
                    </p>

                    {chargeError && <p className="text-sm text-error mb-2">{chargeError}</p>}

                    <div className="flex gap-2 items-center">
                        {posDevices.length > 0 && (
                            <>
                                {posDevices.length > 1 && (
                                    <select value={selectedDeviceId} onChange={e => setSelectedDeviceId(e.target.value)} className="filter-input flex-1">
                                        {posDevices.map(d => (
                                            <option key={d.id} value={d.id}>{d.friendlyName}{d.location ? ` — ${d.location}` : ''}</option>
                                        ))}
                                    </select>
                                )}
                                <button
                                    onClick={() => handleChargeExtraTerminal(Number(selectedDeviceId))}
                                    className="btn btn-primary"
                                    disabled={chargingExtra || !selectedDeviceId}
                                >
                                    {chargingExtra ? 'Charging...' : 'Charge on Terminal'}
                                </button>
                            </>
                        )}
                        {posDevices.length === 0 && cardPresentRecordOnly && (
                            <button onClick={() => handleChargeExtraTerminal(null)} className="btn btn-primary" disabled={chargingExtra}>
                                {chargingExtra ? 'Recording...' : 'Record Charge (No Terminal)'}
                            </button>
                        )}
                        {posDevices.length === 0 && !cardPresentRecordOnly && (
                            <p className="text-sm text-error">No terminal is paired and record-only charging isn't enabled. Contact support.</p>
                        )}
                        <button onClick={() => setChargePrompt(null)} className="btn btn-secondary" disabled={chargingExtra}>Skip</button>
                    </div>
                </div>
            )}

            {folio.status === 'CLOSED' && incidentCharges.length > 0 && (
                <div className="mb-4">
                    <p className="text-sm text-muted mb-2">Incident Charges</p>
                    <table className="w-full text-sm">
                        <tbody>
                            {incidentCharges.map(charge => (
                                <tr key={charge.id} className="border-b border-tan">
                                    <td className="py-2">{charge.reason}</td>
                                    <td className="py-2 text-right">${charge.amount.toFixed(2)}</td>
                                    <td className="py-2 text-right"><StatusBadge status={charge.status} /></td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {error && <p className="text-sm text-error mb-4">{error}</p>}

            {failedPayments.length > 0 && (
                <div className="mb-4 p-3 rounded bg-error/10 text-error text-sm">
                    {failedPayments.map(p => (
                        <p key={p.id}>{p.kind} payment failed: {p.failureReason ?? 'Unknown error'}. Retry capture, or resolve manually.</p>
                    ))}
                </div>
            )}

            <div className="flex justify-end gap-3">
                <button onClick={onClose} className="btn btn-secondary" disabled={paying}>Close</button>
                {folio.status === 'CLOSED' && (
                    <button onClick={() => setShowIncidentChargeModal(true)} className="btn btn-secondary" disabled={paying}>
                        Charge Incident
                    </button>
                )}
                {folio.status === 'CLOSED' && !folio.paidAt && (
                    <button onClick={handlePay} className="btn btn-primary" disabled={paying}>
                        {paying ? 'Capturing...' : 'Capture Payment'}
                    </button>
                )}
            </div>

            {showIncidentChargeModal && (
                <IncidentChargeRequestModal
                    folioId={folioId}
                    onRequested={handleIncidentChargeRequested}
                    onClose={() => setShowIncidentChargeModal(false)}
                />
            )}
        </Modal>
    )
}

export default FolioModal