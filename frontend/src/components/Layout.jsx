import { NavLink } from "react-router-dom";
import './Layout.css'

export default function Layout({ children }) {
    return (
        <div className="min-h-screen bg-cream">
            <nav className="bg-charcoal border-b-2 border-rust">
                <div className="max-w-6xl mx-auto px-8 h-14 flex items-center justify-between">
                    <div className="nav-logo">
                        Stay<span>Desk</span>
                    </div>
                    <div className="flex gap-6">
                        <NavLink to="/rooms" className={({ isActive }) => `text-sm font-medium transition-colors ${isActive ? 'text-warm-yellow' : 'text-white/70 hover:text-white'}`}>Rooms</NavLink>
                        <NavLink to="/reservations" className={({ isActive }) => `text-sm font-medium transition-colors ${isActive ? 'text-warm-yellow' : 'text-white/70 hover:text-white'}`}>Reservations</NavLink>
                    </div>
                </div>
            </nav>
            <main className="max-w-6x1 mx-auto px-8 py-8">
                {children}
            </main>
        </div>
    )
}