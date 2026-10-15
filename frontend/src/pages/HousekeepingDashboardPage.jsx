import { useEffect, useState } from 'react'
import { formatDistanceToNow } from 'date-fns'
import { getHousekeepingBoard, updateHousekeepingStatus } from '../api/housekeepingApi'
import AssignHousekeepingModal from '../components/AssignHousekeepingModal'
import StatusBadge from '../components/StatusBadge'

export default function HousekeepingDashboardPage() {
    const [board, setBoard] = useState([])
    const [loading, setLoading] = useState(true)
    const [statusFilter, setStatusFilter] = useState('ALL')
    const [assignTarget, setAssignTarget] = useState(null)
    const [error, setError] = useState(null)

    useEffect(() => { fetchBoard() }, [])

    async function fetchBoard() {
        setLoading(true)
        const res = await getHousekeepingBoard()
        setBoard(res.data ?? [])
        setLoading(false)
    }

    async function handleStatusChange(roomId, status) {
        setError(null)
        try {
            await updateHousekeepingStatus(roomId, status)
            fetchBoard()
        } catch (err) {
            setError(err.response?.data ?? 'Failed to update status.')
        }
    }

    const displayed = board.filter(r => statusFilter === 'ALL' || r.status === statusFilter)
        .sort((a, b) => a.roomNumber - b.roomNumber)

    return (
        <div>
            <div className="page-header mb-6">
                <h1 className="section-title">Housekeeping</h1>
            </div>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : (
                <>
                    <div className="flex gap-2 mb-6 flex-wrap">
                        {['ALL', 'DIRTY', 'CLEAN', 'INSPECTED'].map(s => (
                            <button
                                key={s}
                                onClick={() => setStatusFilter(prev => prev === s && s !== 'ALL' ? 'ALL' : s)}
                                className={`filter-btn${statusFilter === s ? ' active' : ''}`}
                            >
                                {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
                            </button>
                        ))}
                    </div>

                    {error && <p className="text-sm text-error mb-4">{error}</p>}

                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        {displayed.map(room => (
                            <div key={room.roomId} className="feat-card flex flex-col gap-3">
                                <div className="flex items-center justify-between">
                                    <span className="font-semibold text-black">Room {room.roomNumber}</span>
                                    <StatusBadge status={room.status} />
                                </div>

                                <p className="text-sm text-muted">
                                    {room.assignedEmployeeName ?? 'Unassigned'}
                                </p>

                                {room.status === 'DIRTY' && room.markedDirtyAt && (
                                    <p className="text-xs text-muted">
                                        Dirty since {formatDistanceToNow(new Date(room.markedDirtyAt), { addSuffix: true })}
                                    </p>
                                )}
                                {room.status === 'CLEAN' && room.cleanedAt && (
                                    <p className="text-xs text-muted">
                                        Cleaned {formatDistanceToNow(new Date(room.cleanedAt), { addSuffix: true })}
                                    </p>
                                )}
                                {room.status === 'INSPECTED' && room.inspectedAt && (
                                    <p className="text-xs text-muted">
                                        Inspected {formatDistanceToNow(new Date(room.inspectedAt), { addSuffix: true })}
                                    </p>
                                )}

                                <div className="flex gap-2 flex-wrap mt-auto">
                                    {room.status === 'DIRTY' && (
                                        <button onClick={() => handleStatusChange(room.roomId, 'CLEAN')} className="btn btn-secondary text-sm">
                                            Mark Clean
                                        </button>
                                    )}
                                    {room.status === 'CLEAN' && (
                                        <button onClick={() => handleStatusChange(room.roomId, 'INSPECTED')} className="btn btn-secondary text-sm">
                                            Mark Inspected
                                        </button>
                                    )}
                                    <button onClick={() => setAssignTarget(room)} className="btn btn-secondary text-sm">
                                        {room.assignedEmployeeName ? 'Reassign' : 'Assign'}
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                </>
            )}

            {assignTarget && (
                <AssignHousekeepingModal
                    roomId={assignTarget.roomId}
                    currentEmployeeId={assignTarget.assignedEmployeeId}
                    onSaved={() => { setAssignTarget(null); fetchBoard() }}
                    onClose={() => setAssignTarget(null)}
                />
            )}
        </div>
    )
}
