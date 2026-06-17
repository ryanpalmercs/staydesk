import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { BedDouble, CalendarDays, DollarSign, LogOut, LayoutDashboard, Menu, Settings } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import './Layout.css'

export default function Layout() {
    const { signOut } = useAuth()
    const [drawerOpen, setDrawerOpen] = useState(false)

    const closeDrawer = () => setDrawerOpen(false)

    return (
        <div className="layout">

            {/* Mobile top bar — hidden on desktop */}
            <div className="mobile-topbar">
                <span className="nav-logo">Stay<span>Desk</span></span>
                <button className="hamburger" onClick={() => setDrawerOpen(true)} aria-label="Open menu">
                    <span /><span /><span />
                </button>
            </div>

            {/* Backdrop */}
            <div
                className={`drawer-overlay ${drawerOpen ? 'open' : ''}`}
                onClick={closeDrawer}
            />

            {/* Sidebar */}
            <aside className={`sidebar ${drawerOpen ? 'open' : ''}`}>
                <div className="sidebar-header">
                    <span className="nav-logo">Stay<span>Desk</span></span>
                </div>
                <nav className="sidebar-nav">
                    <NavLink to="/" end className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                        <LayoutDashboard size={18} />
                        <span>Dashboard</span>
                    </NavLink>
                    <NavLink to="/rooms" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                        <BedDouble size={18} />
                        <span>Rooms</span>
                    </NavLink>
                    <NavLink to="/reservations" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                        <CalendarDays size={18} />
                        <span>Reservations</span>
                    </NavLink>
                    <NavLink to="/payroll" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                        <DollarSign size={18} />
                        <span>Payroll</span>
                    </NavLink>
                </nav>
                <div className="sidebar-bottom">
                    <NavLink to="/settings" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                        <Settings size={18} />
                        <span>Settings</span>
                    </NavLink>
                    <button className="sidebar-signout" onClick={signOut}>
                        <LogOut size={18} />
                        <span>Sign out</span>
                    </button>
                </div>
            </aside>

            <main className="main-content">
                <Outlet />
            </main>
        </div>
    )
}