import { useEffect, useRef, useState } from 'react'
import { DateRange } from 'react-date-range'
import { eachDayOfInterval, format, parseISO, subDays } from 'date-fns'
import { getOccupiedDates } from '../api/roomApi'
import 'react-date-range/dist/styles.css'
import 'react-date-range/dist/theme/default.css'
import './ReservationDatePicker.css'

function ReservationDatePicker({ roomId, checkInDate, checkOutDate, onRangeSelected }) {
    const [disabledDates, setDisabledDates] = useState([])
    const [months, setMonths] = useState(window.innerWidth < 768 ? 1 : 2)
    const containerRef = useRef(null)

    useEffect(() => {
        if (!roomId) {
            setDisabledDates([])
            return
        }
        getOccupiedDates(roomId).then(res => {
            const dates = (res.data ?? []).flatMap(range =>
                eachDayOfInterval({
                    start: parseISO(range.checkIn),
                    end: subDays(parseISO(range.checkOut), 1)
                })
            )
            setDisabledDates(dates)
        })
    }, [roomId])

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
                months={months}
                direction={months === 1 ? 'vertical' : 'horizontal'}
                minDate={new Date()}
                disabledDates={disabledDates}
                rangeColors={['#C04A1E']}
            />
        </div>
    )
}

export default ReservationDatePicker