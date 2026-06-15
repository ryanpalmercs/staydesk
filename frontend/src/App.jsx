import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import RoomsPage from './pages/RoomsPage'
import ReservationsPage from './pages/ReservationsPage'
import Layout from './components/Layout'

export default function App() {
    return (
        <BrowserRouter>
            <Layout>
                <Routes>
                    <Route path="/" element={<Navigate to="/reservations" replace />} />
                    <Route path="/rooms" element={<RoomsPage />} />
                    <Route path="/reservations" element={<ReservationsPage />} />
                </Routes>
            </Layout>
        </BrowserRouter>
    )
}