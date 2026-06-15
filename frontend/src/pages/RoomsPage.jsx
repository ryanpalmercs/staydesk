import { useState, useEffect } from "react";
import { getRooms, deleteRoom } from '../api/roomApi'
import RoomModal from '../components/RoomModal'
import StatusBadge from "../components/StatusBadge";

function RoomsPage() {
    const [rooms, setRooms] = useState([])
    const [loading, setLoading] = useState(true)
    const [modalOpen, setModalOpen] = useState(false)
    const [selectedRoom, setSelectedRoom] = useState(null)

    useEffect(() => {
        fetchRooms()
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
            <div className="flex items-center justify-between mb-6">
                <h1 className="section-title">Rooms</h1>
                <button onClick={openCreate} className="btn btn-primary">Add Room</button>
            </div>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : (
                <div className="bg-warm-white rounded-lg border-t-4 border-rust shadow-sm overflow-hidden">
                    <table className="w-full text-left border-collapse">
                        <thead>
                            <tr className="border-b text-sm text-gray-500">
                                <th className="text-xs font-semibold text-muted uppercase tracking-wide px-6 py-3">Room #</th>
                                <th className="text-xs font-semibold text-muted uppercase tracking-wide px-6 py-3">Type</th>
                                <th className="text-xs font-semibold text-muted uppercase tracking-wide px-6 py-3">Nightly Rate</th>
                                <th className="text-xs font-semibold text-muted uppercase tracking-wide px-6 py-3">Status</th>
                                <th className="text-xs font-semibold text-muted uppercase tracking-wide px-6 py-3" />
                            </tr>
                        </thead>
                        <tbody>
                            {rooms.map(room => (
                                <tr key={room.id} className="hover:bg-tan/30 border-b border-tan">
                                    <td className="px-6 py-4">{room.roomNumber}</td>
                                    <td className="px-6 py-4">{room.type}</td>
                                    <td className="px-6 py-4">${room.nightlyRate}</td>
                                    <td className="px-6 py-4"><StatusBadge status={room.status} /></td>
                                    <td className="px-6 py-4 flex gap-3 justify-end">
                                        <button onClick={() => openEdit(room)} className="text-brown hover:text-rust text-sm font-medium">Edit</button>
                                        <button onClick={() => handleDelete(room.id)} className="text-muted hover:text-rust text-sm font-medium">Delete</button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {modalOpen && (
                <RoomModal room={selectedRoom} onSaved={handleSaved} onClose={() => setModalOpen(false)} />
            )}
        </div>
    )
}

export default RoomsPage