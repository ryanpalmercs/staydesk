import { useEffect, useRef, useState } from 'react'
import { DateRange } from 'react-date-range'
import { format, parseISO } from 'date-fns'
import { getRoomTypeOccupiedDates } from '../api/roomTypeApi'
import 'react-date-range/dist/styles.css'
import 'react-date-range/dist/theme/default.css'
import './ReservationDatePicker.css'

function ReservationDatePicker({ roomTypeId, checkInDate, checkOutDate, onRangeSelected, excludeReservationId }) {
    const [disabledDates, setDisabledDates] = useState([])
    const [months, setMonths] = useState(window.innerWidth < 768 ? 1 : 2)
    const [focusedRange, setFocusedRange] = useState([0, 0])
    const containerRef = useRef(null)

    useEffect(() => {
        if (!roomTypeId) {
            setDisabledDates([])
            return
        }
        getRoomTypeOccupiedDates(roomTypeId, excludeReservationId).then(res => {
            setDisabledDates((res.data ?? []).map(date => parseISO(date)))
        })
    }, [roomTypeId, excludeReservationId])

    useEffect(() => {
        if (!containerRef.current) {
            return
        }

        const observer = new ResizeObserver(entries => {
            setMonths(entries[0].contentRect.width >= 600 ? 2 : 1)
        })
        observer.observe(containerRef.current)
        return () => observer.disconnect()
    }, [])

    const selection = {
        startDate: checkInDate ? parseISO(checkInDate) : new Date(),
        endDate: checkOutDate ? parseISO(checkOutDate) : new Date(),
        key: 'selection'
    }

    function handleChange(item) {
        const { startDate, endDate } = item.selection
        onRangeSelected({
            checkInDate: format(startDate, 'yyyy-MM-dd'),
            checkOutDate: format(endDate, 'yyyy-MM-dd')
        })
    }

    return (
        <div ref={containerRef} className="flex justify-center">
            <DateRange
                ranges={[selection]}
                onChange={handleChange}
                focusedRange={focusedRange}
                onRangeFocusChange={setFocusedRange}
                months={months}
                direction={months === 1 ? 'vertical' : 'horizontal'}
                minDate={new Date()}
                disabledDates={disabledDates}
                rangeColors={['#334428']}
            />
        </div>
    )
}

export default ReservationDatePicker