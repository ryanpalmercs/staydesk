import { useState, useEffect } from "react";
import { getRooms, deleteRoom } from '../api/roomApi'
import { getRoomTypes } from '../api/roomTypeApi'
import RoomModal from '../components/RoomModal'
import RoomAccessLogModal from '../components/RoomAccessLogModal'
import StatusBadge from "../components/StatusBadge";
import { useAuth } from "../contexts/AuthContext"
import { StickyNote } from "lucide-react";

function RoomsPage() {
    const { role } = useAuth()
    const [rooms, setRooms] = useState([])
    const [roomTypes, setRoomTypes] = useState([])
    const [loading, setLoading] = useState(true)
    const [modalOpen, setModalOpen] = useState(false)
    const [selectedRoom, setSelectedRoom] = useState(null)
    const [accessLogRoom, setAccessLogRoom] = useState(null)
    const [statusFilter, setStatusFilter] = useState('ALL')
    const displayed = rooms.filter(r => statusFilter === 'ALL' || r.status === statusFilter)
        .sort((a, b) => a.roomNumber - b.roomNumber)
    const roomTypeName = id => roomTypes.find(rt => rt.id === id)?.name.replace('_', ' ') ?? ''
    const [expandedNoteId, setExpandedNoteId] = useState(null)

    useEffect(() => {
        fetchRooms()
        getRoomTypes().then(res => setRoomTypes(res.data ?? []))
    }, [])

    async function fetchRooms() {
        setLoading(true)
        const response = await getRooms()
        setRooms(response.data)
        setLoading(false)
    }

    function openCreate() {
        setSelectedRoom(null)
        setModalOpen(true)
    }

    function openEdit(room) {
        setSelectedRoom(room)
        setModalOpen(true)
    }

    async function handleDelete(id) {
        await deleteRoom(id)
        fetchRooms()
    }

    function handleSaved() {
        setModalOpen(false)
        fetchRooms()
    }

    return (
        <div>
            <div className="page-header mb-6">
                <h1 className="section-title">Rooms</h1>
                <button onClick={openCreate} className="btn btn-primary">Add Room</button>
            </div>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : (
                <>
                    <div className="flex gap-2 mb-6 flex-wrap">
                        {['ALL', 'AVAILABLE', 'OCCUPIED', 'MAINTENANCE'].map(s => (
                            <button
                                key={s}
                                onClick={() => setStatusFilter(prev => prev === s && s !== 'ALL' ? 'ALL' : s)}
                                className={`filter-btn${statusFilter === s ? ' active' : ''}`}
                            >
                                {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
                            </button>
                        ))}
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        {displayed.map(room => (
                            <div key={room.id} className="feat-card flex flex-col gap-3">
                                <div className="flex items-center justify-between">
                                    <span className="font-semibold text-black">Room {room.roomNumber}</span>
                                    <div className="flex items-center gap-2">
                                        {room.status === 'MAINTENANCE' && room.maintenanceNote && (
                                            <button
                                                type="button"
                                                title={room.maintenanceNote}
                                                onClick={() => setExpandedNoteId(prev => prev === room.id ? null : room.id)}
                                                className="text-muted cursor-help"
                                            >
                                                <StickyNote size={16} />
                                            </button>
                                        )}
                                        <StatusBadge status={room.status} />
                                    </div>
                                </div>
                                <p className="text-sm text-muted">{roomTypeName(room.roomTypeId)}</p>
                                {expandedNoteId === room.id && (
                                    <p className="text-xs text-muted italic">{room.maintenanceNote}</p>
                                )}
                                <div className="flex gap-3 justify-end">
                                    {role === 'ADMIN' && room.sifelyLockId != null && (
                                        <button onClick={() => setAccessLogRoom(room)} className="text-muted hover:text-green text-sm font-medium">Access Log</button>
                                    )}
                                    <button onClick={() => openEdit(room)} className="text-muted hover:text-green text-sm font-medium">Edit</button>
                                    <button onClick={() => handleDelete(room.id)} className="text-muted hover:text-green text-sm font-medium">Delete</button>
                                </div>
                            </div>
                        ))}
                    </div>
                </>
            )}

            {modalOpen && (
                <RoomModal room={selectedRoom} onSaved={handleSaved} onClose={() => setModalOpen(false)} />
            )}

            {accessLogRoom && (
                <RoomAccessLogModal room={accessLogRoom} onClose={() => setAccessLogRoom(null)} />
            )}
        </div>
    )
}

export default RoomsPage