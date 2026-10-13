import { useEffect, useState } from 'react'
import { getLowStockItems } from '../api/inventoryApi'

const POLL_INTERVAL_MS = 60000

export default function LowStockToast() {
    const [items, setItems] = useState([])
    const [dismissedIds, setDismissedIds] = useState([])

    useEffect(() => {
        function poll() {
            getLowStockItems()
                .then(res => setItems(res.data ?? []))
                .catch(err => console.error('Failed to poll low-stock inventory:', err))
        }

        poll()
        const interval = setInterval(poll, POLL_INTERVAL_MS)
        return () => clearInterval(interval)
    }, [])

    function handleDismiss(itemId) {
        setDismissedIds(prev => [...prev, itemId])
    }

    const visible = items.filter(i => !dismissedIds.includes(i.itemId))

    if (visible.length === 0) {
        return null
    }

    return (
        <div className="fixed top-4 right-4 z-50 flex flex-col gap-2 max-w-sm">
            {visible.map(item => (
                <div key={item.itemId} className="bg-warm-white border-l-4 border-error rounded-lg shadow-lg p-4">
                    <p className="text-sm font-medium text-black">Low stock — {item.name}</p>
                    <p className="text-xs text-muted mt-1">
                        {item.currentStock} {item.unit} left (threshold {item.lowStockThreshold})
                    </p>
                    <div className="flex justify-end mt-2">
                        <button
                            onClick={() => handleDismiss(item.itemId)}
                            className="text-sm font-medium text-green hover:text-black"
                        >
                            Dismiss
                        </button>
                    </div>
                </div>
            ))}
        </div>
    )
}
