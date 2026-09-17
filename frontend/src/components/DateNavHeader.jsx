import { addDays, format } from "date-fns"
import { ArrowLeftIcon, ArrowRightIcon } from "lucide-react"

export function todayStr() {
    return format(new Date(), 'yyyy-MM-dd')
}

// Shared by the dashboard's quick-action modals (Checking In/Out, Occupancy): a prev/next day
// stepper plus a native date input so staff can jump straight to any date instead of only
// stepping one day at a time.
function DateNavHeader({ viewDate, onChange, minDate, offsetToday }) {
    function shiftDate(deltaDays) {
        onChange(format(addDays(new Date(viewDate + 'T12:00:00'), deltaDays), 'yyyy-MM-dd'))
    }

    return (
        <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 mb-4">
            <div />
            <div className="flex items-center gap-3 justify-self-center">
                <button
                    type="button"
                    onClick={() => shiftDate(-1)}
                    disabled={minDate != null && viewDate <= minDate}
                    className="text-muted hover:text-green disabled:opacity-30 disabled:hover:text-muted"
                    aria-label="Previous day"
                >
                    <ArrowLeftIcon size={20} />
                </button>
                <input
                    type="date"
                    value={viewDate}
                    min={minDate}
                    onChange={e => e.target.value && onChange(e.target.value)}
                    className="filter-input text-sm font-medium text-black text-center"
                    aria-label="Jump to date"
                />
                <button type="button" onClick={() => shiftDate(1)} className="text-muted hover:text-green" aria-label="Next day">
                    <ArrowRightIcon size={20} />
                </button>
            </div>
            <button
                type="button"
                onClick={() => onChange(todayStr())}
                disabled={viewDate === todayStr()}
                className={`filter-btn disabled:opacity-40 disabled:cursor-not-allowed justify-self-end${offsetToday ? ' mr-4' : ''}`}
            >
                Today
            </button>
        </div>
    )
}

export default DateNavHeader
