import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { Clock, BedDouble, CalendarDays, DollarSign, LogOut, LayoutDashboard, Menu, Settings, Users, UserSearch, BarChart2, Wrench, ClipboardList } from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import DoorAccessToast from './DoorAccessToast'
import './Layout.css'

export default function Layout() {
    const { role, signOut, user } = useAuth()
    const [drawerOpen, setDrawerOpen] = useState(false)

    const closeDrawer = () => setDrawerOpen(false)

    const adminOnly = role === 'ADMIN'
    const showRooms = ['ADMIN', 'MANAGER', 'FRONT_DESK'].includes(role)
    const showDashboard = ['ADMIN', 'MANAGER', 'FRONT_DESK', 'HOUSEKEEPING'].includes(role)
    const showReservations = ['ADMIN', 'MANAGER', 'FRONT_DESK'].includes(role)
    const showIncidentCharges = ['ADMIN', 'MANAGER'].includes(role)
    const showTimesheet = !adminOnly

    return (
        <div className="layout">

            {showReservations && <DoorAccessToast />}

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
                    {showDashboard && (
                        <NavLink to={role === 'HOUSEKEEPING' ? '/housekeeping' : '/'} end className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                            <LayoutDashboard size={18} />
                            <span>Dashboard</span>
                        </NavLink>
                    )}
                    {showTimesheet && (
                        <NavLink to={`/timesheet/${user?.id}`} className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                            <Clock size={18} />
                            <span>Timesheet</span>
                        </NavLink>
                    )}
                    {showRooms && (
                        <NavLink to="/rooms" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                            <BedDouble size={18} />
                            <span>Rooms</span>
                        </NavLink>
                    )}
                    {showReservations && (
                        <NavLink to="/reservations" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                            <CalendarDays size={18} />
                            <span>Reservations</span>
                        </NavLink>
                    )}
                    {showReservations && (
                        <NavLink to="/guests" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                            <UserSearch size={18} />
                            <span>Guests</span>
                        </NavLink>
                    )}
                    {showIncidentCharges && (
                        <NavLink to="/incident-charges" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                            <Wrench size={18} />
                            <span>Incident Charges</span>
                        </NavLink>
                    )}
                    {adminOnly && (
                        <>
                            <NavLink to="/employees" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                                <Users size={18} />
                                <span>Employees</span>
                            </NavLink>
                            <NavLink to="/payroll" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                                <DollarSign size={18} />
                                <span>Payroll</span>
                            </NavLink>
                            <NavLink to="/backlog-check-in" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                                <ClipboardList size={18} />
                                <span>Backlog Check-In</span>
                            </NavLink>
                        </>
                    )}
                </nav>
                <div className="sidebar-bottom">
                    {adminOnly && (
                        <NavLink to="/reports" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                            <BarChart2 size={18} />
                            <span>Reports</span>
                        </NavLink>
                    )}

                    {adminOnly && (
                        <NavLink to="/settings" className={({ isActive }) => isActive ? 'active' : ''} onClick={closeDrawer}>
                            <Settings size={18} />
                            <span>Settings</span>
                        </NavLink>
                    )}
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