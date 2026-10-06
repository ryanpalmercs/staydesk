import { useEffect, useState } from 'react'
import { addDays, format } from 'date-fns'
import { ArrowLeftIcon, ArrowRightIcon } from 'lucide-react'
import { getRoomTypeAvailabilityGrid } from '../api/roomTypeApi'
import { todayStr } from './DateNavHeader'
import './RoomTypeAvailabilityGrid.css'

const WINDOW_DAYS = 14

function RoomTypeAvailabilityGrid({ roomTypes }) {
    const [rangeStart, setRangeStart] = useState(todayStr)
    const [grid, setGrid] = useState([])

    const dates = Array.from({ length: WINDOW_DAYS }, (_, i) => format(addDays(new Date(rangeStart + 'T12:00:00'), i), 'yyyy-MM-dd'))
    const rangeEnd = format(addDays(new Date(rangeStart + 'T12:00:00'), WINDOW_DAYS), 'yyyy-MM-dd')

    useEffect(() => {
        getRoomTypeAvailabilityGrid(rangeStart, rangeEnd)
            .then(res => setGrid(res.data))
    }, [rangeStart, rangeEnd])

    const countsByRoomType = {}
    for (const row of grid) {
        countsByRoomType[row.roomTypeId] ??= {}
        countsByRoomType[row.roomTypeId][row.date] = row.availableCount
    }

    const sortedRoomTypes = [...roomTypes].sort((a, b) => a.name.localeCompare(b.name))
    const totalsByDate = Object.fromEntries(dates.map(date => [
        date,
        sortedRoomTypes.reduce((sum, rt) => sum + (countsByRoomType[rt.id]?.[date] ?? 0), 0)
    ]))

    return (
        <div className="availability-grid">
            <div className="flex items-center justify-between mb-3">
                <h2 className="text-lg text-black font-semibold">Availability</h2>
                <div className="flex items-center gap-3">
                    <button
                        type="button"
                        onClick={() => setRangeStart(format(addDays(new Date(rangeStart + 'T12:00:00'), -WINDOW_DAYS), 'yyyy-MM-dd'))}
                        className="text-muted hover:text-green"
                        aria-label="Previous range"
                    >
                        <ArrowLeftIcon size={20} />
                    </button>
                    <button
                        type="button"
                        onClick={() => setRangeStart(todayStr())}
                        disabled={rangeStart === todayStr()}
                        className="filter-btn disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                        Today
                    </button>
                    <button
                        type="button"
                        onClick={() => setRangeStart(format(addDays(new Date(rangeStart + 'T12:00:00'), WINDOW_DAYS), 'yyyy-MM-dd'))}
                        className="text-muted hover:text-green"
                        aria-label="Next range"
                    >
                        <ArrowRightIcon size={20} />
                    </button>
                </div>
            </div>

            <div className="availability-grid-scroll">
                <table className="availability-grid-table">
                    <thead>
                        <tr>
                            <th>Room Type</th>
                            {dates.map(date => (
                                <th key={date}>{format(new Date(date + 'T12:00:00'), 'EEE M/d')}</th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        <tr className="availability-grid-total-row">
                            <td>TOTAL AVAIL</td>
                            {dates.map(date => <td key={date}>{totalsByDate[date]}</td>)}
                        </tr>
                        {sortedRoomTypes.map(rt => (
                            <tr key={rt.id}>
                                <td>{rt.name.replace('_', ' ')}</td>
                                {dates.map(date => <td key={date}>{countsByRoomType[rt.id]?.[date] ?? '—'}</td>)}
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    )
}

export default RoomTypeAvailabilityGrid
