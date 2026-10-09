import { useRef, useState } from 'react'
import { DateRange } from 'react-date-range'
import 'react-date-range/dist/styles.css'
import 'react-date-range/dist/theme/default.css'
import './ReservationDatePicker.css'
import { useOutsideClick } from '../hooks/useOutsideClick'
import { useEscapeKey } from '../hooks/useEscapeKey'
import { useIsDesktop } from '../hooks/useIsDesktop'
import { toIsoDate, parseIsoDate, todayIso, addDaysIso } from '../utils/date'

const MAX_GUEST_COUNT = 4

function formatDisplayDate(iso) {
    return parseIsoDate(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

export default function BookingSearchWidget({ className = '' }) {
    const [checkInDate, setCheckInDate] = useState(todayIso())
    const [checkOutDate, setCheckOutDate] = useState(addDaysIso(todayIso(), 1))
    const [adults, setAdults] = useState(2)
    const [children, setChildren] = useState(0)
    const [openPopover, setOpenPopover] = useState(null) // null | 'dates' | 'guests'
    const isDesktop = useIsDesktop()

    const datesRef = useRef(null)
    const guestsRef = useRef(null)

    useOutsideClick(datesRef, () => setOpenPopover(p => (p === 'dates' ? null : p)))
    useOutsideClick(guestsRef, () => setOpenPopover(p => (p === 'guests' ? null : p)))
    useEscapeKey(() => setOpenPopover(null))

    const guestCount = adults + children
    const bookingHref = `/book?checkIn=${checkInDate}&checkOut=${checkOutDate}&adults=${adults}&children=${children}`

    return (
        <div className={`bg-warm-white rounded-xl shadow-2xl border border-tan flex flex-col sm:flex-row sm:items-stretch overflow-visible ${className}`}>
            <div ref={datesRef} className="relative flex-1 flex flex-col sm:flex-row border-b sm:border-b-0 sm:border-r border-tan">
                <button type="button" onClick={() => setOpenPopover(p => (p === 'dates' ? null : 'dates'))}
                        className="flex-1 text-left px-4 py-3">
                    <span className="text-xs font-semibold text-muted uppercase tracking-wide block">Check-in</span>
                    <span className="text-sm font-medium">{formatDisplayDate(checkInDate)}</span>
                </button>
                <button type="button" onClick={() => setOpenPopover(p => (p === 'dates' ? null : 'dates'))}
                        className="flex-1 text-left px-4 py-3 border-t sm:border-t-0 sm:border-l border-tan">
                    <span className="text-xs font-semibold text-muted uppercase tracking-wide block">Check-out</span>
                    <span className="text-sm font-medium">{formatDisplayDate(checkOutDate)}</span>
                </button>

                {openPopover === 'dates' && (
                    <div className="absolute z-20 top-full left-0 mt-2 bg-cream rounded-lg shadow-2xl border border-tan overflow-hidden">
                        <DateRange
                            ranges={[{ startDate: parseIsoDate(checkInDate), endDate: parseIsoDate(checkOutDate), key: 'selection' }]}
                            onChange={item => {
                                const { startDate, endDate } = item.selection
                                setCheckInDate(toIsoDate(startDate))
                                setCheckOutDate(startDate.getTime() === endDate.getTime() ? addDaysIso(toIsoDate(startDate), 1) : toIsoDate(endDate))
                            }}
                            months={isDesktop ? 2 : 1}
                            direction={isDesktop ? 'horizontal' : 'vertical'}
                            minDate={new Date()}
                            showMonthAndYearPickers={false}
                            showDateDisplay={false}
                            dragSelectionEnabled={true}
                            monthDisplayFormat="MMMM yyyy"
                            rangeColors={['#334428']}
                        />
                        <div className="p-3 border-t border-tan flex justify-end">
                            <button type="button" onClick={() => setOpenPopover(null)} className="btn-primary !w-auto !px-6 !py-2 text-sm">Done</button>
                        </div>
                    </div>
                )}
            </div>

            <div ref={guestsRef} className="relative border-b sm:border-b-0 sm:border-r border-tan">
                <button type="button" onClick={() => setOpenPopover(p => (p === 'guests' ? null : 'guests'))}
                        className="w-full sm:w-auto text-left px-4 py-3">
                    <span className="text-xs font-semibold text-muted uppercase tracking-wide block">Guests</span>
                    <span className="text-sm font-medium">{guestCount} guest{guestCount > 1 ? 's' : ''}</span>
                </button>

                {openPopover === 'guests' && (
                    <div className="absolute z-20 top-full left-0 mt-2 w-72 bg-warm-white rounded-lg shadow-2xl border border-tan p-4 flex flex-col gap-4">
                        <div className="flex items-center justify-between">
                            <span className="text-sm font-semibold">Adults</span>
                            <div className="flex items-center gap-3">
                                <button type="button" onClick={() => setAdults(a => Math.max(1, a - 1))} disabled={adults <= 1}
                                        className="w-9 h-9 rounded-full border border-tan flex items-center justify-center disabled:opacity-40" aria-label="Decrease adults">−</button>
                                <span className="font-semibold w-4 text-center">{adults}</span>
                                <button type="button" onClick={() => setAdults(a => Math.min(MAX_GUEST_COUNT - children, a + 1))} disabled={guestCount >= MAX_GUEST_COUNT}
                                        className="w-9 h-9 rounded-full border border-tan flex items-center justify-center disabled:opacity-40" aria-label="Increase adults">+</button>
                            </div>
                        </div>
                        <div className="flex items-center justify-between">
                            <span className="text-sm font-semibold">Children</span>
                            <div className="flex items-center gap-3">
                                <button type="button" onClick={() => setChildren(c => Math.max(0, c - 1))} disabled={children <= 0}
                                        className="w-9 h-9 rounded-full border border-tan flex items-center justify-center disabled:opacity-40" aria-label="Decrease children">−</button>
                                <span className="font-semibold w-4 text-center">{children}</span>
                                <button type="button" onClick={() => setChildren(c => Math.min(MAX_GUEST_COUNT - adults, c + 1))} disabled={guestCount >= MAX_GUEST_COUNT}
                                        className="w-9 h-9 rounded-full border border-tan flex items-center justify-center disabled:opacity-40" aria-label="Increase children">+</button>
                            </div>
                        </div>
                        <button type="button" onClick={() => setOpenPopover(null)} className="btn-primary !w-auto self-end !px-6 !py-2 text-sm">Done</button>
                    </div>
                )}
            </div>

            <a href={bookingHref} target="_blank" rel="noopener noreferrer"
               className="btn-primary !rounded-none sm:!rounded-r-xl !rounded-b-xl sm:!rounded-bl-none flex items-center justify-center px-8">
                Check availability
            </a>
        </div>
    )
}
