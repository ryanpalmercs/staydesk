import { useEffect, useState } from "react";
import { getReservationEstimate } from "../api/reservationApi";
import { settleWalkInStay, settleWalkinStayTerminal } from "../api/folioApi";
import Modal from "./Modal"
import PaymentMethodStep from "./PaymentMethodStep";

function SettleStayPaymentModal({ folioId, reservation, onSettled, onSkip }) {
    const [stayTotal, setStayTotal] = useState(null)

    useEffect(() => {
        getReservationEstimate({
            rateType: reservation.rateType,
            guestCount: reservation.guestCount,
            checkInDate: reservation.checkInDate,
            checkOutDate: reservation.checkOutDate
        }).then(res => setStayTotal(res.data.total)).catch(() => setStayTotal(null))
    }, [])

    return (
        <Modal onClose={onSkip} size="md">
            <h2 className="text-lg text-black font-semibold mb-4">Charge for Stay</h2>
            <PaymentMethodStep
                amount={stayTotal}
                amountLabel="Total Charge"
                description="Charge the full stay for this booking now, before checking guests in."
                dual={false}
                submitLabel="Charge"
                onSubmitToken={async (roomToken) => {
                    await settleWalkInStay(folioId, roomToken)
                    onSettled()
                }}
                onSubmitTerminal={async (deviceId) => {
                    await settleWalkinStayTerminal(folioId, deviceId)
                    onSettled()
                }}
                onCancel={onSkip}
                terminalErrorMessage="Failed to charge card. It may have been declined on the terminal."
                recordOnlyErrorMessage="Failed to charge card."
            />
        </Modal>
    )
}

export default SettleStayPaymentModal