import { useState, useEffect } from "react"
import { Link } from "react-router-dom"
import { getGuests } from "../api/guestApi"
import { formatPhone } from "../utils/phone"
import StatusBadge from "../components/StatusBadge"

function GuestsPage() {
    const [guests, setGuests] = useState([])
    const [loading, setLoading] = useState(true)
    const [search, setSearch] = useState('')

    useEffect(() => {
        fetchGuests()
    }, [])

    async function fetchGuests() {
        setLoading(true)
        const res = await getGuests()
        setGuests(res.data ?? [])
        setLoading(false)
    }

    const query = search.trim().toLowerCase()
    const digitQuery = query.replace(/\D/g, '')

    const filtered = guests
        .filter(g => {
            if (!query) return true
            if (g.name.toLowerCase().includes(query)) return true
            if (digitQuery && g.phoneNumber.replace(/\D/g, '').includes(digitQuery)) return true
            return false
        })
        .sort((a, b) => a.lastName.localeCompare(b.lastName))

    return (
        <div>
            <div className="page-header mb-6">
                <h1 className="section-title">Guests</h1>
            </div>

            <div className="filter-bar mb-6">
                <div>
                    <label className="text-muted block text-xs mb-1">Search</label>
                    <input
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Name or phone number"
                        className="filter-input"
                    />
                </div>
            </div>

            {loading ? (
                <p className="text-gray-500">Loading...</p>
            ) : filtered.length === 0 ? (
                <p className="text-sm text-muted">No guests found.</p>
            ) : (
                <div className="flex flex-col gap-3">
                    {filtered.map(guest => (
                        <Link key={guest.id} to={`/guest/${guest.id}`} className="feat-card block hover:shadow-md transition-shadow">
                            <div className="flex items-start justify-between gap-4 mb-2">
                                <span className="font-semibold text-black">{guest.name}</span>
                                <div className="flex gap-2">
                                    {guest.flagged && <StatusBadge status="FLAGGED" />}
                                    {guest.legalHold && <StatusBadge status="LEGAL_HOLD" />}
                                </div>
                            </div>
                            <div className="flex gap-4 text-sm text-muted">
                                <span>{guest.email}</span>
                                <span>{formatPhone(guest.phoneNumber)}</span>
                            </div>
                        </Link>
                    ))}
                </div>
            )}
        </div>
    )
}

export default GuestsPage
