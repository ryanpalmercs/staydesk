import { RefreshCw, X } from 'lucide-react'

function StaleDataBanner({ stale, onRefresh, onDismiss }) {
    if (!stale) return null

    return (
        <div className="flex items-center justify-between gap-3 bg-amber-50 border border-amber-300 text-amber-900 rounded-lg px-4 py-3 mb-4">
            <div className="flex items-center gap-2">
                <RefreshCw size={16} />
                <span className="text-sm font-medium">New activity elsewhere — this view may be out of date.</span>
            </div>
            <div className="flex items-center gap-3">
                <button onClick={onRefresh} className="btn btn-secondary text-sm">Refresh</button>
                <button onClick={onDismiss} aria-label="Dismiss" className="text-amber-900 hover:text-black">
                    <X size={16} />
                </button>
            </div>
        </div>
    )
}

export default StaleDataBanner
