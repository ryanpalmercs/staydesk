import { useState, useEffect } from "react"
import { useParams } from "react-router-dom"
import { getGuest } from "../api/guestApi"
import { formatPhone } from "../utils/phone"

function GuestProfilePage() {
    const { id } = useParams()

    const [guest, setGuest] = useState(null)
    const [loading, setLoading] = useState(true)
    const [notFound, setNotFound] = useState(false)

    useEffect(() => {
        fetchGuest()
    }, [id])

    async function fetchGuest() {
        setLoading(true)
        setNotFound(false)
        try {
            const res = await getGuest(id)
            setGuest(res.data)
        } catch (err) {
            if (err.response?.status === 404) {
                setNotFound(true)
            }
        }
        setLoading(false)
    }

    if (loading) {
        return <p className="text-gray-500">Loading...</p>
    }

    if (notFound) {
        return <p className="text-muted">Guest not found.</p>
    }

    return (
        <div>
            <div className="page-header mb-6">
                <h1 className="section-title">{guest.name}</h1>
            </div>
            <div className="flex gap-2 mb-6 flex-wrap">
                <div>
                    <span className="block text-sm text-muted mb-1">Email</span>
                    <span className="text-sm text-charcoal">{guest.email}</span>
                </div>
                <div>
                    <span className="block text-sm text-muted mb-1">Phone Number</span>
                    <span className="text-sm text-charcoal">{formatPhone(guest.phoneNumber)}</span>
                </div>
            </div>
        </div>
    )
}

export default GuestProfilePage