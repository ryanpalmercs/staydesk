import dayjs from "dayjs"
import { useState, useEffect } from "react"
import { exportReport, getReportSummary } from "../api/reportApi"
import { FileDown, Minus, TrendingDown, TrendingUp } from "lucide-react"
import { displayPrice } from "../utils/price"
import './ReportsPage.css'

function getComparisonDates(startDate, endDate) {
    const start = dayjs(startDate)
    const end = dayjs(endDate)
    const duration = end.diff(start, 'day')

    return {
        comparisonStartDate: start.subtract(duration + 1, 'day').format('YYYY-MM-DD'),
        comparisonEndDate: start.subtract(1, 'day').format('YYYY-MM-DD'),
    }
}

function getPreset(type, customStart, customEnd) {
    const today = dayjs()
    switch (type) {
        case 'this_month': {
            const startDate = today.startOf('month').format('YYYY-MM-DD')
            const endDate = today.endOf('month').format('YYYY-MM-DD')
            return { startDate, endDate, ...getComparisonDates(startDate, endDate) }
        }

        case 'last_month': {
            const last = today.subtract(1, 'month')
            const startDate = last.startOf('month').format('YYYY-MM-DD')
            const endDate = last.endOf('month').format('YYYY-MM-DD')
            return { startDate, endDate, ...getComparisonDates(startDate, endDate) }
        }

        case 'last_30': {
            const startDate = today.subtract(29, 'day').format('YYYY-MM-DD')
            const endDate = today.format('YYYY-MM-DD')
            return { startDate, endDate, ...getComparisonDates(startDate, endDate) }
        }

        case 'last_6_months': {
            const last = today.subtract(6, 'month')
            const startDate = last.startOf('month').format('YYYY-MM-DD')
            const endDate = today.endOf('month').format('YYYY-MM-DD')
            return { startDate, endDate, ...getComparisonDates(startDate, endDate) }
        }

        case 'custom': {
            return { startDate: customStart, endDate: customEnd, ...getComparisonDates(customStart, customEnd) }
        }

        default:
            return getPreset('this_month')
    }
}

function ComparisonDelta({ label, current, previous, format }) {
    const diff = current - previous
    const pct = previous !== 0 ? ((diff / previous) * 100).toFixed(1) : null
    const direction = diff > 0 ? 'up' : diff < 0 ? 'down' : 'flat'

    return (
        <div className={`comparison-delta ${direction}`}>
            <span>{label}:</span>
            <span>{format(current)}</span>
            {pct !== null && (
                <span className="delta-pct">
                    {direction === 'up' && <TrendingUp size={14} />}
                    {direction === 'down' && <TrendingDown size={14} />}
                    {direction === 'flat' && <Minus size={14} />}
                    {Math.abs(pct)}%
                </span>
            )}
        </div>
    )
}


function ReportsPage() {
    const [preset, setPreset] = useState('this_month')
    const [customStart, setCustomStart] = useState('')
    const [customEnd, setCustomEnd] = useState('')
    const [dates, setDates] = useState(() => getPreset('this_month'))
    const [report, setReport] = useState(null)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)

    useEffect(() => {
        const { startDate, endDate, comparisonStartDate, comparisonEndDate } = dates
        setLoading(true)
        setError(null)
        getReportSummary(startDate, endDate, comparisonStartDate, comparisonEndDate)
            .then(res => setReport(res.data))
            .catch(() => setError('Failed to load report.'))
            .finally(() => setLoading(false))
    }, [dates])

    function applyPreset(type) {
        setPreset(type)
        if (type != 'custom') {
            setDates(getPreset(type))
        }
    }

    function applyCustom() {
        if (customStart && customEnd && customStart <= customEnd) {
            setDates(getPreset('custom', customStart, customEnd))
        }
    }

    function handleExport(format) {
        exportReport(report, format).then(res => {
            const url = URL.createObjectURL(res.data)
            const a = document.createElement('a')
            a.href = url
            a.download = `report-${report.startDate}-${report.endDate}.${format}`
            a.click()
            URL.revokeObjectURL(url)
        })
    }

    return (
        <div className="page-container">
            <div>
                <h1 className="section-title">Reports</h1>
            </div>

            <div className="report-presets">
                {['this_month', 'last_month', 'last_30', 'last_6_months', 'custom'].map(p => (
                    <button key={p} className={`preset-btn ${preset === p ? 'active' : ''}`} onClick={() => applyPreset(p)}>
                        {{ this_month: 'This Month', last_month: 'Last Month', last_30: 'Last 30 Days', last_6_months: 'Last 6 Months', custom: 'Custom' }[p]}
                    </button>
                ))}
            </div>

            {preset === 'custom' && (
                <div className="report-custom-range">
                    <input type="date" value={customStart} onChange={e => setCustomStart(e.target.value)} />
                    <span>to</span>
                    <input type="date" value={customEnd} onChange={e => setCustomEnd(e.target.value)} />
                    <button onClick={applyCustom}>Apply</button>
                </div>
            )}

            {loading && <p>Loading...</p>}
            {error && <p className="error">{error}</p>}

            {report && !loading && (
                <>
                    <div className="report-actions">
                        <button className="btn-secondary" onClick={() => handleExport('csv')}>
                            <FileDown size={15} /> CSV
                        </button>
                        <button className="btn-secondary" onClick={() => handleExport('pdf')}>
                            <FileDown size={15} /> PDF
                        </button>
                    </div>
                    {/* Stat cards */}
                    <div className="report-cards">
                        <div className="report-card">
                            <span className="report-card-label">Revenue</span>
                            <span className="report-card-value">{displayPrice(report.totalRevenue)}</span>
                        </div>
                        <div className="report-card">
                            <span className="report-card-label">Tax Collected</span>
                            <span className="report-card-value">{displayPrice(report.totalTax)}</span>
                        </div>
                        <div className="report-card">
                            <span className="report-card-label">Occupancy</span>
                            <span className="report-card-value">{(report.occupancyRate * 100).toFixed(1)}%</span>
                        </div>
                        <div className="report-card">
                            <span className="report-card-label">Avg Nightly Rate</span>
                            <span className="report-card-value">{displayPrice(report.averageNightlyRate)}</span>
                        </div>
                        <div className="report-card">
                            <span className="report-card-label">Occupied Nights</span>
                            <span className="report-card-value">{report.occupiedNightCount}</span>
                        </div>
                    </div>

                    {/* Period comparison */}
                    <div className="report-comparison">
                        <span className="report-comparison-label">
                            vs. {report.comparison.startDate} – {report.comparison.endDate}
                        </span>
                        <ComparisonDelta label="Revenue" current={report.totalRevenue} previous={report.comparison.totalRevenue} format={displayPrice} />
                        <ComparisonDelta label="Occupancy" current={report.occupancyRate} previous={report.comparison.occupancyRate} format={v => (v * 100).toFixed(1) + '%'} />
                    </div>

                    {/* Guest count breakdown */}
                    <div className="report-section">
                        <h2>By Guest Count</h2>
                        <table className="report-table">
                            <thead>
                                <tr>
                                    <th>Guests</th>
                                    <th>Reservations</th>
                                    <th>Revenue</th>
                                </tr>
                            </thead>
                            <tbody>
                                {report.guestCountBreakdown.map(row => (
                                    <tr key={row.guestCount}>
                                        <td>{row.guestCount} {row.guestCount === 1 ? 'guest' : 'guests'}</td>
                                        <td>{row.reservationCount}</td>
                                        <td>{displayPrice(row.revenue)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {/* Room breakdown */}
                    <div className="report-section">
                        <h2>By Room</h2>
                        <table className="report-table">
                            <thead>
                                <tr>
                                    <th>Room</th>
                                    <th>Booked Nights</th>
                                    <th>Occupancy</th>
                                    <th>Revenue</th>
                                </tr>
                            </thead>
                            <tbody>
                                {report.roomBreakDown.map(row => (
                                    <tr key={row.roomId}>
                                        <td>Room {row.roomNumber}</td>
                                        <td>{row.bookedNights} / {row.totalNights}</td>
                                        <td>{(row.occupancyRate * 100).toFixed(1)}%</td>
                                        <td>{displayPrice(row.revenue)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </>
            )}
        </div>
    )
}

export default ReportsPage