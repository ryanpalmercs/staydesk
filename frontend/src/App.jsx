import { BrowserRouter, Routes, Route } from 'react-router-dom'
import RoomsPage from './pages/RoomsPage'
import ReservationsPage from './pages/ReservationsPage'

export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<div>Staydesk</div>} />
                <Route path="/rooms" element={<RoomsPage />} />
                <Route path="/reservations" element={<ReservationsPage />} />
            </Routes>
        </BrowserRouter>
    )
}