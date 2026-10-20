import { useEffect, useState } from 'react'
import { getInventoryBoard } from '../api/inventoryApi'
import { useAuth } from '../contexts/AuthContext'
import InventoryItemModal from '../components/InventoryItemModal'
import RestockModal from '../components/RestockModal'
import LogUsageModal from '../components/LogUsageModal'

export default function InventoryPage() {
    const { role } = useAuth()
    const canManage = ['ADMIN', 'MANAGER'].includes(role)

    const [items, setItems] = useState([])
    const [loading, setLoading] = useState(true)
    const [editTarget, setEditTarget] = useState(null)
    const [addingItem, setAddingItem] = useState(false)
    const [restockTarget, setRestockTarget] = useState(null)
    const [usageTarget, setUsageTarget] = useState(null)

    useEffect(() => { fetchBoard() }, [])

    async function fetchBoard() {
        setLoading(true)
        const res = await getInventoryBoard()
        setItems(res.data ?? [])
        setLoading(false)
    }

    return (
        <div>
            <div className="page-header mb-6 flex items-center justify-between">
                <h1 className="section-title">Inventory</h1>
                {canManage && (
                    <button onClick={() => setAddingItem(true)} className="btn btn-primary">Add Item</button>
                )}
            </div>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                    {items.map(item => (
                        <div key={item.itemId} className="feat-card flex flex-col gap-3">
                            <div className="flex items-center justify-between">
                                <span className="font-semibold text-black">{item.name}</span>
                                {item.lowStock && (
                                    <span className="text-xs font-medium px-2.5 py-1 rounded-full bg-error/10 text-error">
                                        Low Stock
                                    </span>
                                )}
                            </div>

                            <p className="text-sm text-muted">{item.category}</p>

                            <p className="text-sm text-black">
                                {item.currentStock} {item.unit} <span className="text-muted">(threshold {item.lowStockThreshold})</span>
                            </p>

                            <div className="flex gap-2 flex-wrap mt-auto">
                                <button onClick={() => setUsageTarget(item)} className="btn btn-secondary text-sm">
                                    Log Usage
                                </button>
                                {canManage && (
                                    <>
                                        <button onClick={() => setRestockTarget(item)} className="btn btn-secondary text-sm">
                                            Restock
                                        </button>
                                        <button onClick={() => setEditTarget(item)} className="btn btn-secondary text-sm">
                                            Edit
                                        </button>
                                    </>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {addingItem && (
                <InventoryItemModal
                    onSaved={() => { setAddingItem(false); fetchBoard() }}
                    onClose={() => setAddingItem(false)}
                />
            )}
            {editTarget && (
                <InventoryItemModal
                    item={editTarget}
                    onSaved={() => { setEditTarget(null); fetchBoard() }}
                    onClose={() => setEditTarget(null)}
                />
            )}
            {restockTarget && (
                <RestockModal
                    item={restockTarget}
                    onSaved={() => { setRestockTarget(null); fetchBoard() }}
                    onClose={() => setRestockTarget(null)}
                />
            )}
            {usageTarget && (
                <LogUsageModal
                    item={usageTarget}
                    onSaved={() => { setUsageTarget(null); fetchBoard() }}
                    onClose={() => setUsageTarget(null)}
                />
            )}
        </div>
    )
}
