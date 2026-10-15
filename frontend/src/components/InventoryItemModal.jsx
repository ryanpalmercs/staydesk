import { useState } from "react"
import { createInventoryItem, updateInventoryItem } from "../api/inventoryApi"
import Modal from "./Modal"

function InventoryItemModal({ item, onSaved, onClose }) {
    const [name, setName] = useState(item?.name ?? '')
    const [category, setCategory] = useState(item?.category ?? '')
    const [unit, setUnit] = useState(item?.unit ?? '')
    const [lowStockThreshold, setLowStockThreshold] = useState(item?.lowStockThreshold ?? 0)
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)

    async function handleSubmit(e) {
        e.preventDefault()
        setError(null)
        setSubmitting(true)

        try {
            if (item) {
                await updateInventoryItem(item.itemId, name, category, unit, Number(lowStockThreshold), true)
            } else {
                await createInventoryItem(name, category, unit, Number(lowStockThreshold))
            }
            onSaved()
        } catch {
            setError('Failed to save item.')
        }

        setSubmitting(false)
    }

    return (
        <Modal onClose={onClose} size="sm">
            <h2 className="text-lg text-black font-semibold mb-4">{item ? 'Edit Item' : 'Add Item'}</h2>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div>
                    <label className="block text-sm text-muted mb-1">Name</label>
                    <input value={name} onChange={e => setName(e.target.value)} required className="filter-input" />
                </div>
                <div>
                    <label className="block text-sm text-muted mb-1">Category</label>
                    <input value={category} onChange={e => setCategory(e.target.value)} required className="filter-input" />
                </div>
                <div>
                    <label className="block text-sm text-muted mb-1">Unit</label>
                    <input value={unit} onChange={e => setUnit(e.target.value)} required className="filter-input" placeholder="e.g. each, roll" />
                </div>
                <div>
                    <label className="block text-sm text-muted mb-1">Low Stock Threshold</label>
                    <input type="number" min="0" value={lowStockThreshold}
                        onChange={e => setLowStockThreshold(e.target.value)} required className="filter-input" />
                </div>

                {error && <p className="text-sm text-error">{error}</p>}

                <div className="flex justify-end gap-3 mt-2">
                    <button type="button" onClick={onClose} className="btn btn-secondary">Cancel</button>
                    <button type="submit" className="btn btn-primary" disabled={submitting}>
                        {submitting ? 'Saving...' : 'Save'}
                    </button>
                </div>
            </form>
        </Modal>
    )
}

export default InventoryItemModal
