import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { useRef } from 'react'

export default function ProtectedRoute() {
    const { session, loading } = useAuth()
    const hadSession = useRef(false)

    if (session) {
        hadSession.current = true
    }

    if (loading) {
        return null
    }

    if (!session) {
        return <Navigate to="/login" state={{ expired: hadSession.current }} replace />
    }
    
    return <Outlet />
}