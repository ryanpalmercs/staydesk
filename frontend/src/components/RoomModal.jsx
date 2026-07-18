import { useEffect, useRef, useState } from "react"
import { createRoom, updateRoom } from "../api/roomApi"
import { getRoomTypes } from "../api/roomTypeApi"
import Modal from "./Modal"

function RoomModal({ room, onSaved, onClose }) {
    const isEditing = room != null

    const [roomTypes, setRoomTypes] = useState([])
    const [form, setForm] = useState({
        roomNumber: room?.roomNumber ?? '',
        roomTypeId: room?.roomTypeId ?? '',
        status: room?.status ?? 'AVAILABLE',
        maintenanceNote: room?.maintenanceNote ?? ''
    })
    const initialFormRef = useRef(form)
    const isDirty = JSON.stringify(form) !== JSON.stringify(initialFormRef.current)

    useEffect(() => {
        getRoomTypes().then(res => {
            setRoomTypes([...(res.data ?? [])].sort((a, b) => a.name.localeCompare(b.name)))
        })
    }, [])

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
        <Modal onClose={onClose} size="md" isDirty={isDirty}>
            <h2 className="text-lg text-black font-semibold mb-4">
                {isEditing ? 'Edit Room' : 'Add Room'}
            </h2>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div>
                    <label className="block text-sm text-muted mb-1">Room Number</label>
                    <input type="number" name="roomNumber" value={form.roomNumber} onChange={handleChange} className="filter-input" required />
                </div>

                <div>
                    <label className="block text-sm text-muted mb-1">Type</label>
                    <select name="roomTypeId" value={form.roomTypeId} onChange={handleChange} className="filter-input">
                        <option value="">Select a room type...</option>
                        {roomTypes.map(rt => (
                            <option key={rt.id} value={rt.id}>{rt.name.replace('_', ' ')}</option>
                        ))}
                    </select>
                </div>

                {isEditing && (
                    <div>
                        <label className="block text-sm text-muted mb-1">Status</label>
                        <select name="status" value={form.status} onChange={handleChange} className="filter-input">
                            <option value="AVAILABLE">Available</option>
                            <option value="OCCUPIED">Occupied</option>
                            <option value="MAINTENANCE">Maintenance</option>
                        </select>
                    </div>
                )}

                {form.status === 'MAINTENANCE' && (
                    <div>
                        <label className="block text-sm text-muted mb-1">Maintenance Note</label>
                        <textarea name="maintenanceNote" value={form.maintenanceNote} onChange={handleChange} className="filter-input w-full" rows={3} required />                        </div>
                )}

                <div className="flex justify-end gap-3 mt-2">
                    <button type="button" onClick={onClose} className="btn btn-secondary">
                        Cancel
                    </button>
                    <button type="submit" className="btn btn-primary" disabled={isEditing && !isDirty}>
                        {isEditing ? 'Save' : 'Add Room'}
                    </button>
                </div>
            </form>
        </Modal>
    )
}

export default RoomModal