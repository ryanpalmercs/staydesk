import AssignRoomModal from "./AssignRoomModal";
import CheckInPaymentModal from "./CheckInPaymentModal";
import ExtendStayModal from "./ExtendStayModal";
import FolioModal from "./FolioModal";
import MoveRoomModal from "./MoveRoomModal";
import ReservationSummaryModal from "./ReservationSummaryModal";

function ReservationActionModals({
    reservations, guestsMap, roomLabel, fetchData,
    selectedEvent, setSelectedEvent,
    checkInTarget, setCheckInTarget,
    folioId, setFolioId, folioReservationId,
    extendTarget, setExtendTarget,
    assignRoomTarget, setAssignRoomTarget,
    moveRoomTarget, setMoveRoomTarget,
    handleCheckInConfirmed, handleTerminalCheckInConfirmed,
    handleViewFolio, handleCheckOut,
}) {
    return (
        <>
            {selectedEvent && !checkInTarget && (
                <ReservationSummaryModal
                    reservation={selectedEvent}
                    guest={guestsMap[selectedEvent.guestId]}
                    roomLabel={roomLabel(selectedEvent)}
                    onClose={() => setSelectedEvent(null)}
                    onCheckIn={() => setCheckInTarget(selectedEvent.reservationId)}
                    onCheckOut={handleCheckOut}
                    onViewFolio={handleViewFolio}
                    onExtend={() => {
                        setExtendTarget(reservations.find(r => r.id === selectedEvent.reservationId))
                        setSelectedEvent(null)
                    }}
                    onAssignRoom={() => {
                        setAssignRoomTarget(reservations.find(r => r.id === selectedEvent.reservationId))
                        setSelectedEvent(null)
                    }}
                    onMoveRoom={() => {
                        setMoveRoomTarget(reservations.find(r => r.id === selectedEvent.reservationId))
                        setSelectedEvent(null)
                    }}
                />
            )}

            {extendTarget != null && (
                <ExtendStayModal
                    reservation={extendTarget}
                    onSaved={() => { setExtendTarget(null); fetchData() }}
                    onClose={() => setExtendTarget(null)}
                />
            )}

            {assignRoomTarget != null && (
                <AssignRoomModal
                    roomTypeId={assignRoomTarget.roomTypeId}
                    checkInDate={assignRoomTarget.checkInDate}
                    checkOutDate={assignRoomTarget.checkOutDate}
                    reservationId={assignRoomTarget.id}
                    onSaved={() => { setAssignRoomTarget(null); fetchData() }}
                    onClose={() => setAssignRoomTarget(null)}
                />
            )}

            {moveRoomTarget != null && (
                <MoveRoomModal
                    reservation={moveRoomTarget}
                    onSaved={() => { setMoveRoomTarget(null); fetchData() }}
                    onClose={() => setMoveRoomTarget(null)}
                />
            )}

            {checkInTarget != null && (
                <CheckInPaymentModal
                    reservationId={checkInTarget}
                    reservation={reservations.find(r => r.id === checkInTarget)}
                    onConfirm={handleCheckInConfirmed}
                    onConfirmTerminal={handleTerminalCheckInConfirmed}
                    onClose={() => setCheckInTarget(null)}
                />
            )}

            {folioId && (
                <FolioModal folioId={folioId} reservationId={folioReservationId} onClose={() => setFolioId(null)} onPaid={fetchData} />
            )}
        </>
    )
}

export default ReservationActionModals