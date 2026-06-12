import { useState, useEffect } from "react";
import { getRooms, deleteRoom } from '../api/roomApi'
import RoomModal from '../components/RoomModal'

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
        console.log(import.meta.env.VITE_API_BASE_URL)
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
        <div className="p-8">
            <div className="flex items-center justify-between mb-6">
                <h1 className="text-2x1 font-semibold">Rooms</h1>
                <button onClick={openCreate} className="bg-black text-white px-4 py-2 rounded hover:bg-gray-800">Add Room</button>
            </div>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : (
                <table className="w-full text-left border-collapse">
                    <thead>
                        <tr className="border-b text-sm text-gray-500">
                            <th className="pb-2">Room #</th>
                            <th className="pb-2">Type</th>
                            <th className="pb-2">Nightly Rate</th>
                            <th className="pb-2">Status</th>
                            <th className="pb-2" />
                        </tr>
                    </thead>
                    <tbody>
                        {rooms.map(room => (
                            <tr key={room.id} className="border-b hover:bg-gray-50">
                                <td className="py-3">{room.roomNumber}</td>
                                <td className="py-3">{room.type}</td>
                                <td className="py-3">${room.nightlyRate}</td>
                                <td className="py-3">{room.status}</td>
                                <td className="py-3 flex gap-3 justify-end">
                                    <button onClick={() => openEdit(room)} className="text-sm text-blue-600 hover:underline">Edit</button>
                                    <button onClick={() => handleDelete(room.id)} className="text-sm text-red-600 hover:underline">Delete</button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            )}

            {modalOpen && (
                <RoomModal room={selectedRoom} onSaved={handleSaved} onClose={() => setModalOpen(false)} />
            )}
        </div>
    )
}

export default RoomsPage