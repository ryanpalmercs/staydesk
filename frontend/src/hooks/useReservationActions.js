import { useState } from "react"
import { checkIn, checkInTerminal, checkOut } from "../api/reservationApi"
import { getFolioByReservationId } from "../api/folioApi"

function useReservationActions({ fetchData }) {
    const [selectedEvent, setSelectedEvent] = useState(null)
    const [checkInTarget, setCheckInTarget] = useState(null)
    const [folioId, setFolioId] = useState(null)
    const [folioReservationId, setFolioReservationId] = useState(null)
    const [extendTarget, setExtendTarget] = useState(null)
    const [assignRoomTarget, setAssignRoomTarget] = useState(null)
    const [moveRoomTarget, setMoveRoomTarget] = useState(null)

    async function handleCheckInConfirmed(roomId, incidentalsPaymentMethodId, roomPaymentMethodId) {
        const res = await checkIn(checkInTarget, roomId, incidentalsPaymentMethodId, roomPaymentMethodId)
        setSelectedEvent(null)
        fetchData()
        return res.data.doorAccessStatus
    }

    async function handleTerminalCheckInConfirmed(roomId, posDeviceId) {
        const res = await checkInTerminal(checkInTarget, roomId, posDeviceId)
        setSelectedEvent(null)
        fetchData()
        return res.data.doorAccessStatus
    }

    async function handleViewFolio() {
        try {
            const res = await getFolioByReservationId(selectedEvent.reservationId)
            setFolioId(res.data.id)
            setFolioReservationId(selectedEvent.reservationId)
            setSelectedEvent(null)
        } catch (err) {
            console.error('Failed to find folio:', err)
        }
    }

    async function checkOutReservation(reservationId) {
        const res = await checkOut(reservationId)
        const folioRes = await getFolioByReservationId(reservationId)
        setFolioId(folioRes.data.id)
        setFolioReservationId(reservationId)
        fetchData()
        return res
    }

    async function handleCheckOut() {
        try {
            await checkOutReservation(selectedEvent.reservationId)
            setSelectedEvent(null)
        } catch (err) {
            console.error('Check-out failed:', err)
        }
    }

    return {
        selectedEvent, setSelectedEvent,
        checkInTarget, setCheckInTarget, 
        folioId, setFolioId, folioReservationId,
        extendTarget, setExtendTarget,
        assignRoomTarget, setAssignRoomTarget,
        moveRoomTarget, setMoveRoomTarget,
        handleCheckInConfirmed, handleTerminalCheckInConfirmed,
        handleViewFolio, checkOutReservation, handleCheckOut,
    }
}

export default useReservationActions