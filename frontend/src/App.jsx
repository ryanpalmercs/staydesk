import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import Hotjar from '@hotjar/browser'
import ProtectedRoute from './components/ProtectedRoute'
import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import RoomsPage from './pages/RoomsPage'
import ReservationsPage from './pages/ReservationsPage'
import PayrollPage from './pages/PayrollPage'
import DashboardPage from './pages/DashboardPage'
import HousekeepingDashboardPage from './pages/HousekeepingDashboardPage'
import SettingsPage from './pages/SettingsPage'
import EmployeesPage from './pages/EmployeesPage'
import { useEffect, useState } from 'react'
import api from './api/baseApi'
import TimesheetPage from './pages/TimesheetPage'
import ReportsPage from './pages/ReportsPage'
import RoomAccessLogPage from './pages/RoomAccessLogPage'
import GuestProfilePage from './pages/GuestProfilePage'
import GuestsPage from './pages/GuestsPage'
import PrivacyPolicyPage from './pages/PrivacyPolicy'

const HOTJAR_ID = import.meta.env.VITE_HOTJAR_ID
const HOTJAR_VERSION = 6


export default function App() {
    const [wakingUp, setWakingUp] = useState(false)

    useEffect(() => {
        if (HOTJAR_ID) {
            Hotjar.init(parseInt(HOTJAR_ID), HOTJAR_VERSION)
        }
    }, [])

    useEffect(() => {
        const timer = setTimeout(() => setWakingUp(true), 0)
        api.get('/actuator/health')
            .then(() => { clearTimeout(timer); setWakingUp(false) })
            .catch(() => { clearTimeout(timer); setWakingUp(false) })
        return () => clearTimeout(timer)
    }, [])

    return (
        <>
            {wakingUp && (
                <div className="waking-up-banner">
                    Server waking up, please wait...
                </div>
            )}
            <BrowserRouter>
                <AuthProvider>
                    <Routes>
                        <Route path="/login" element={<LoginPage />} />
                        <Route path="/privacy-policy" element={<PrivacyPolicyPage />} />
                        <Route element={<ProtectedRoute />}>
                            <Route element={<Layout />}>
                                <Route path="/" element={<DashboardPage />} />
                                <Route path="/rooms" element={<RoomsPage />} />
                                <Route path="/reservations" element={<ReservationsPage />} />
                                <Route path="/timesheet/:id" element={<TimesheetPage />} />
                                <Route path="/guest/:id" element={<GuestProfilePage />} />
                                <Route path="/guests" element={<GuestsPage />} />
                            </Route>
                        </Route>
                        <Route element={<ProtectedRoute allowedRoles={['HOUSEKEEPING']} />}>
                            <Route element={<Layout />}>
                                <Route path="/housekeeping" element={<HousekeepingDashboardPage />} />
                            </Route>
                        </Route>
                        <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
                            <Route element={<Layout />}>
                                <Route path="/employees" element={<EmployeesPage />} />
                                <Route path="/payroll" element={<PayrollPage />} />
                                <Route path="/settings" element={<SettingsPage />} />
                                <Route path="/reports" element={<ReportsPage />} />
                                <Route path="/rooms/:id/access-log" element={<RoomAccessLogPage />} />
                            </Route>
                        </Route>
                    </Routes>
                </AuthProvider>
            </BrowserRouter>
        </>
    )
}