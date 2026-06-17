import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import RoomsPage from './pages/RoomsPage'
import ReservationsPage from './pages/ReservationsPage'
import PayrollPage from './pages/PayrollPage'

export default function App() {
    return (
        <BrowserRouter>
            <AuthProvider>
                <Routes>
                    <Route path="/login" element={<LoginPage />} />
                    <Route element={<ProtectedRoute />}>
                        <Route element={<Layout />}>
                            <Route path="/" element={<Navigate to="/reservations" replace />} />
                            <Route path="/rooms" element={<RoomsPage />} />
                            <Route path="/reservations" element={<ReservationsPage />} />
                            <Route path="/payroll" element={<PayrollPage />} />
                        </Route>
                    </Route>
                </Routes>
            </AuthProvider>
        </BrowserRouter>
    )
}