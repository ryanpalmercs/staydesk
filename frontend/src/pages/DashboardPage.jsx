import { useState } from 'react'
import { format } from 'date-fns'
import { formatGuestName } from '../utils/guestName'
import { useAuth } from '../contexts/AuthContext'
import CheckingInTodayModal from '../components/CheckingInTodayModal'
import CheckingOutTodayModal from '../components/CheckingOutTodayModal'
import OccupancyModal from '../components/OccupancyModal'
import RoomTypeAvailabilityGrid from '../components/RoomTypeAvailabilityGrid'
import useReservationData from '../hooks/useReservationData'
import useReservationActions from '../hooks/useReservationActions'
import './DashboardPage.css'
import ReservationActionModals from '../components/ReservationActionModals'

function DashboardPage() {
    const { displayName } = useAuth()
    const [showCheckingInModal, setShowCheckingInModal] = useState(false)
    const [showCheckingOutModal, setShowCheckingOutModal] = useState(false)
    const [showOccupancyModal, setShowOccupancyModal] = useState(false)

    const data = useReservationData()
    const actions = useReservationActions({ fetchData: data.fetchData })

    async function handleCheckOutFromModal(reservationId) {
        try {
            await actions.checkOutReservation(reservationId)
            setShowCheckingOutModal(false)
        } catch (err) {
            console.error('Check-out failed:', err)
        }
    }

    const today = format(new Date(), 'yyyy-MM-dd')
    const occupiedCount = data.rooms.filter(r => r.status === 'OCCUPIED').length
    const availableCount = data.rooms.filter(r => r.status === 'AVAILABLE').length
    const todayCheckIns = data.reservations.filter(r => r.checkInDate === today && r.status === 'CONFIRMED')
    const todayCheckOuts = data.reservations.filter(r => r.checkOutDate === today && r.status === 'CHECKED_IN')

    return (
        <div className="dashboard">
            {displayName ? (
                <h1 className="section-title">{displayName}'s Dashboard</h1>
            ) : (
                <h1 className="section-title">Dashboard</h1>
            )}

            <div className="dashboard-stats">
                <button type="button" onClick={() => setShowOccupancyModal(true)} className="stat-card text-left">
                    <div>
                        <div className="stat-label">Occupancy</div>
                        <div className="stat-value">{occupiedCount} / {data.rooms.length}</div>
                        <div className="stat-sub">{availableCount} available</div>
                    </div>
                </button>
                <button type="button" onClick={() => setShowCheckingInModal(true)} className="stat-card text-left">
                    <div>
                        <div className="stat-label">Checking In Today</div>
                        <div className="stat-value">{todayCheckIns.length}</div>
                        <ul className="stat-list">
                            {todayCheckIns.slice(0, 5).map(r => (
                                <li key={r.id}>
                                    {formatGuestName(data.guestsMap[r.guestId])} — {data.roomLabel(r)}
                                </li>
                            ))}
                        </ul>
                    </div>
                </button>
                <button type="button" onClick={() => setShowCheckingOutModal(true)} className="stat-card text-left">
                    <div>
                        <div className="stat-label">Checking Out Today</div>
                        <div className="stat-value">{todayCheckOuts.length}</div>
                        <ul className="stat-list">
                            {todayCheckOuts.slice(0, 5).map(r => (
                                <li key={r.id}>
                                    {formatGuestName(data.guestsMap[r.guestId])} — {data.roomLabel(r)}
                                </li>
                            ))}
                        </ul>
                    </div>
                </button>
            </div>

            <RoomTypeAvailabilityGrid roomTypes={data.roomTypes} />

            {showCheckingInModal && (
                <CheckingInTodayModal
                    reservations={data.reservations}
                    guestsMap={data.guestsMap}
                    roomLabel={data.roomLabel}
                    onClose={() => setShowCheckingInModal(false)}
                    onCheckIn={reservationId => {
                        setShowCheckingInModal(false)
                        actions.setCheckInTarget(reservationId)
                    }}
                />
            )}

            {showCheckingOutModal && (
                <CheckingOutTodayModal
                    reservations={data.reservations}
                    guestsMap={data.guestsMap}
                    roomLabel={data.roomLabel}
                    onClose={() => setShowCheckingOutModal(false)}
                    onCheckOut={handleCheckOutFromModal}
                    onExtend={reservationId => {
                        setShowCheckingOutModal(false)
                        actions.setExtendTarget(data.reservations.find(r => r.id === reservationId))
                    }}
                />
            )}

            {showOccupancyModal && (
                <OccupancyModal
                    rooms={data.rooms}
                    roomTypesMap={data.roomTypesMap}
                    guestsMap={data.guestsMap}
                    reservations={data.reservations}
                    onClose={() => setShowOccupancyModal(false)}
                    onSelectRoom={r => {
                        setShowOccupancyModal(false)
                        actions.setSelectedEvent({
                            reservationId: r.id,
                            status: r.status,
                            guestId: r.guestId,
                            roomId: r.roomId,
                            roomTypeId: r.roomTypeId,
                            roomNumber: data.roomsMap[r.roomId]?.roomNumber ?? Infinity,
                            checkInDate: r.checkInDate,
                            checkOutDate: r.checkOutDate
                        })
                    }}
                />
            )}

            <ReservationActionModals {...actions} {...data} />
        </div>
    )
}

export default DashboardPage