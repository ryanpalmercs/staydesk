import { useState } from "react"
import { restockItem } from "../api/inventoryApi"
import Modal from "./Modal"

function RestockModal({ item, onSaved, onClose }) {
    const [quantity, setQuantity] = useState('')
    const [note, setNote] = useState('')
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    async function handleSubmit(e) {
        e.preventDefault()
        setError(null)
        setSubmitting(true)

        try {
            await restockItem(item.itemId, Number(quantity), note || null)
            onSaved()
        } catch {
            setError('Failed to record restock.')
        }

        setSubmitting(false)
    }

    return (
        <Modal onClose={onClose} size="sm">
            <h2 className="text-lg text-black font-semibold mb-4">Restock {item.name}</h2>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div>
                    <label className="block text-sm text-muted mb-1">Quantity received ({item.unit})</label>
                    <input type="number" min="1" value={quantity} onChange={e => setQuantity(e.target.value)}
                        required className="filter-input" autoFocus />
                </div>
                <div>
                    <label className="block text-sm text-muted mb-1">Note (optional)</label>
                    <input value={note} onChange={e => setNote(e.target.value)} className="filter-input" />
                </div>

                {error && <p className="text-sm text-error">{error}</p>}

                <div className="flex justify-end gap-3 mt-2">
                    <button type="button" onClick={onClose} className="btn btn-secondary">Cancel</button>
                    <button type="submit" className="btn btn-primary" disabled={submitting}>
                        {submitting ? 'Saving...' : 'Restock'}
                    </button>
                </div>
            </form>
        </Modal>
    )
}

export default RestockModal
