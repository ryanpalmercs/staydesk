import { useEffect, useRef, useState } from "react"
import { createRoom, updateRoom } from "../api/roomApi"
import { getRoomTypes } from "../api/roomTypeApi"
import { getSifelyLocks } from "../api/sifelyApi"
import { useAuth } from "../contexts/AuthContext"
import Modal from "./Modal"

function RoomModal({ room, onSaved, onClose }) {
    const isEditing = room != null
    const { role } = useAuth()
    const canManageLocks = role === 'ADMIN'

    const [roomTypes, setRoomTypes] = useState([])
    const [sifelyLocks, setSifelyLocks] = useState([])
    const [form, setForm] = useState({
        roomNumber: room?.roomNumber ?? '',
        roomTypeId: room?.roomTypeId ?? '',
        status: room?.status ?? 'AVAILABLE',
        maintenanceNote: room?.maintenanceNote ?? '',
        sifelyLockId: room?.sifelyLockId ?? ''
    })
    const initialFormRef = useRef(form)
    const isDirty = JSON.stringify(form) !== JSON.stringify(initialFormRef.current)

    useEffect(() => {
        getRoomTypes().then(res => {
            setRoomTypes([...(res.data ?? [])].sort((a, b) => a.name.localeCompare(b.name)))
        })
    }, [])

    useEffect(() => {
        if (!canManageLocks) {
            return
        }

        getSifelyLocks().then(res => setSifelyLocks(res.data ?? []))
    }, [canManageLocks])

    function handleChange(e) {
        setForm({ ...form, [e.target.name]: e.target.value })
    }

    async function handleSubmit(e) {
        e.preventDefault()
        try {
            const payload = { ...form, sifelyLockId: form.sifelyLockId === '' ? null : Number(form.sifelyLockId) }
            let result
            if (isEditing) {
                result = await updateRoom(room.id, { ...room, ...payload })
            } else {
                result = await createRoom(payload)
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

                {canManageLocks && (
                    <div>
                        <label className="block text-sm text-muted mb-1">Door Lock</label>
                        <select name="sifelyLockId" value={form.sifelyLockId} onChange={handleChange} className="filter-input">
                            <option value="">Unassigned</option>
                            {sifelyLocks.map(lock => (
                                <option key={lock.lockId} value={lock.lockId}>
                                    {lock.lockAlias || lock.lockName || lock.lockId}
                                </option>
                            ))}
                        </select>
                    </div>
                )}

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