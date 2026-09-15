import Modal from "./Modal"
import StatusBadge from "./StatusBadge"

function OccupancyModal({ rooms, roomTypesMap, guestsMap, reservationByRoomId, onClose, onSelectRoom }) {
    const sortedRooms = [...rooms].sort((a, b) => a.roomNumber - b.roomNumber)

    return (
        <Modal onClose={onClose} size="lg">
            <h2 className="text-lg text-black font-semibold mb-4">Occupancy</h2>

            <ul className="flex flex-col gap-3 max-h-96 overflow-y-auto">
                {sortedRooms.map(room => {
                    const reservation = reservationByRoomId[room.id]
                    const guest = reservation ? guestsMap[reservation.guestId] : null
                    const roomTypeName = roomTypesMap[room.roomTypeId]?.name.replace('_', ' ') ?? ''

                    const content = (
                        <>
                            <div className="flex items-center justify-between">
                                <span className="font-semibold text-black">Room {room.roomNumber}</span>
                                <StatusBadge status={room.status} />
                            </div>
                            <p className="text-sm text-muted">
                                {guest ? `${guest.firstName} ${guest.lastName}` : roomTypeName}
                            </p>
                        </>
                    )

                    return (
                        <li key={room.id}>
                            {reservation ? (
                                <button
                                    type="button"
                                    onClick={() => onSelectRoom(reservation)}
                                    className="w-full bg-warm-white rounded flex flex-col gap-1 p-4 text-left border-2 border-tan hover:border-black transition-colors"
                                >
                                    {content}
                                </button>
                            ) : (
                                <div className="w-full bg-warm-white rounded flex flex-col gap-1 p-4 text-left border-2 border-tan opacity-70">
                                    {content}
                                </div>
                            )}
                        </li>
                    )
                })}
            </ul>

            <div className="flex justify-end mt-4">
                <button type="button" onClick={onClose} className="btn btn-secondary">Close</button>
            </div>
        </Modal>
    )
}

export default OccupancyModal
