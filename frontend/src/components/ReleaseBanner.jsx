import { useState } from 'react'
import { TriangleAlert, X } from 'lucide-react'
import { isDayBeforeScheduledRelease } from '../utils/releaseSchedule'

function ReleaseBanner() {
    const [dismissed, setDismissed] = useState(false)

    if (dismissed || !isDayBeforeScheduledRelease()) return null

    return (
        <div style={{
            position: 'sticky', top: 0, zIndex: 9999, width: '100%', textAlign: 'center',
            padding: '6px 12px', fontWeight: 700, letterSpacing: '0.05em', fontSize: '0.85rem',
            color: '#F0E7DE', background: '#1A1A1A', display: 'flex', alignItems: 'center',
            justifyContent: 'center', gap: '0.5rem', flexShrink: 0
        }}>
            <TriangleAlert size={20} />
            <span>HEADS UP — WE'RE DEPLOYING AN UPDATE TONIGHT, EXPECT A BRIEF INTERRUPTION</span>
            <button
                type="button"
                onClick={() => setDismissed(true)}
                aria-label="Dismiss"
                style={{ background: 'none', border: 'none', color: '#F0E7DE', cursor: 'pointer', display: 'flex', flexShrink: 0 }}
            >
                <X size={18} />
            </button>
        </div>
    )
}

export default ReleaseBanner
