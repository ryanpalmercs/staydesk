import { BrowserRouter, Routes, Route } from 'react-router-dom'
import RoomsPage from './pages/RoomsPage'

export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<div>Staydesk</div>} />
                <Route path="/rooms" element={<RoomsPage />} />
            </Routes>
        </BrowserRouter>
    )
}