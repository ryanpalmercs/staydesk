import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { BedDouble, CalendarDays, DollarSign, ChevronLeft, ChevronRight, LogOut } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import './Layout.css'

export default function Layout() {
    const { signOut } = useAuth()
    const [collapsed, setCollapsed] = useState(false)

    return (
        <div className="layout">
            <aside className={`sidebar${collapsed ? ' collapsed' : ''}`}>
                <div className="sidebar-header">
                    <div className="sidebar-logo nav-logo">
                        {collapsed ? <>S<span>D</span></> : <>Stay<span>Desk</span></>}
                    </div>                </div>
                <nav className="sidebar-nav">
                    <NavLink to="/rooms" className={({ isActive }) => isActive ? 'active' : ''}>
                        <BedDouble size={18} />
                        {!collapsed && <span>Rooms</span>}
                    </NavLink>
                    <NavLink to="/reservations" className={({ isActive }) => isActive ? 'active' : ''}>
                        <CalendarDays size={18} />
                        {!collapsed && <span>Reservations</span>}
                    </NavLink>
                    <NavLink to="/payroll" className={({ isActive }) => isActive ? 'active' : ''}>
                        <DollarSign size={18} />
                        {!collapsed && <span>Payroll</span>}
                    </NavLink>
                </nav>
                <button className="sidebar-signout" onClick={signOut}>
                    <LogOut size={18} />
                    {!collapsed && <span>Sign out</span>}
                </button>

                <button className="sidebar-toggle" onClick={() => setCollapsed(c => !c)}>
                    {collapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
                </button>
            </aside>
            <main className="main-content">
                <Outlet />
            </main>
        </div>
    )
}