import { useEffect, useState } from "react";
import { getRooms } from "../api/roomApi";
import { getRoomTypes } from "../api/roomTypeApi";
import { getReservations } from "../api/reservationApi";
import { getGuests } from "../api/guestApi";
import { getActivityVersion } from "../api/activityApi";

function useReservationData() {
    const [rooms, setRooms] = useState([])
    const [roomTypes, setRoomTypes] = useState([])
    const [reservations, setReservations] = useState([])
    const [guests, setGuests] = useState([])
    const [asOf, setAsOf] = useState(null)

    function fetchData() {
        Promise.all([getRooms(), getRoomTypes(), getReservations(), getGuests(), getActivityVersion()])
        .then(([r, rt, res, g, v]) => {
            setRooms(r.data);
            setRoomTypes(rt.data);
            setReservations(res.data);
            setGuests(g.data);
            setAsOf(v.data.asOf);
        })
    }
    useEffect(() => { fetchData() }, [])

    const guestsMap = Object.fromEntries(guests.map(g => [g.id, g]))
    const roomsMap = Object.fromEntries(rooms.map(r => [r.id, r]))
    const roomTypesMap = Object.fromEntries(roomTypes.map(rt => [rt.id, rt]))
    const roomLabel = r => roomsMap[r.roomId] ? `Room ${roomsMap[r.roomId].roomNumber}` : `${roomTypesMap[r.roomTypeId]?.name.replace('_', ' ') ?? 'Room'} (unassigned)`

    return { rooms, roomTypes, reservations, guests, guestsMap, roomsMap, roomTypesMap, roomLabel, fetchData, asOf }
}

export default useReservationData