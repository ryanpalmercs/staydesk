import { useState } from "react"
import { createRoom, updateRoom } from "../api/roomApi"

function RoomModal({ room, onSaved, onClose }) {
    const isEditing = room != null

    const [form, setForm] = useState({
        roomNumber: room?.roomNumber ?? '',
        type: room?.type ?? 'TYPE_1',
        nightlyRate: room?.nightlyRate ?? '',
        status: room?.status ?? 'AVAILABLE'
    })

    function handleChange(e) {
        setForm({ ...form, [e.target.name]: e.target.value })
    }

    async function handleSubmit(e) {
        e.preventDefault()
        try {
            let result 
            if (isEditing) {
                result = await updateRoom(room.id, { ...room, ...form })
            } else {
                console.log('form:', form)
                console.log('type:', typeof form, JSON.stringify(form))
                result = await createRoom(form)
            }

            console.log(result)
            onSaved()
        } catch (err) {
            console.error(err)
        }
    }

    return (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-lg p-6 w-full max-w-md shadow-lg">
                <h2 className="text-lg font-semibold mb-4">
                    {isEditing ? 'Edit Room' : 'Add Room'}
                </h2>

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <div>
                        <label className="block text-sm text-gray-600 mb-1">Room Number</label>
                        <input
                            type="number"
                            name="roomNumber"
                            value={form.roomNumber}
                            onChange={handleChange}
                            className="w-full border rounded px-3 py-2 text-sm"
                            required
                        />
                    </div>

                    <div>
                        <label className="block text-sm text-gray-600 mb-1">Type</label>
                        <select
                            name="type"
                            value={form.type}
                            onChange={handleChange}
                            className="w-full border rounded px-3 py-2 text-sm"
                        >
                            <option value="TYPE_1">Type 1</option>
                            <option value="TYPE_2">Type 2</option>
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm text-gray-600 mb-1">Nightly Rate</label>
                        <input
                            type="number"
                            name="nightlyRate"
                            value={form.nightlyRate}
                            onChange={handleChange}
                            className="w-full border rounded px-3 py-2 text-sm"
                            step="0.01"
                            required
                        />
                    </div>

                    {isEditing && (
                        <div>
                            <label className="block text-sm text-gray-600 mb-1">Status</label>
                            <select
                                name="status"
                                value={form.status}
                                onChange={handleChange}
                                className="w-full border rounded px-3 py-2 text-sm"
                            >
                                <option value="AVAILABLE">Available</option>
                                <option value="OCCUPIED">Occupied</option>
                                <option value="MAINTENANCE">Maintenance</option>
                            </select>
                        </div>
                    )}

                    <div className="flex justify-end gap-3 mt-2">
                        <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-black">
                            Cancel
                        </button>
                        <button type="submit" className="px-4 py-2 text-sm bg-black text-white rounded hover:bg-gray-800">
                            {isEditing ? 'Save' : 'Add Room'}
                        </button>
                    </div>
                </form>
            </div >
        </div >
    )
}

export default RoomModal