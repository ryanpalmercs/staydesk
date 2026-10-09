import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import ProtectedRoute from './ProtectedRoute'

vi.mock('../contexts/AuthContext', () => ({
    useAuth: vi.fn()
}))
// WhatsNewGate does its own getCurrentUser() fetch (via api/meApi, which transitively imports
// lib/supabase.js) - stubbed out here since this suite is only about ProtectedRoute's own
// session/role/redirect gating, not WhatsNewGate's release-notes logic (covered separately).
vi.mock('./WhatsNewGate', () => ({
    default: () => <div>WhatsNewGate</div>
}))

import { useAuth } from '../contexts/AuthContext'

function LoginStub() {
    const location = useLocation()
    return <div>Login Page (expired: {String(!!location.state?.expired)})</div>
}

function renderAtDashboard(allowedRoles) {
    return render(
        <MemoryRouter initialEntries={['/dashboard']}>
            <Routes>
                <Route element={<ProtectedRoute allowedRoles={allowedRoles} />}>
                    <Route path="/dashboard" element={<div>Dashboard Content</div>} />
                </Route>
                <Route path="/login" element={<LoginStub />} />
                <Route path="/" element={<div>Home Page</div>} />
            </Routes>
        </MemoryRouter>
    )
}

describe('ProtectedRoute', () => {
    it('renders nothing while auth is still loading', () => {
        useAuth.mockReturnValue({ session: undefined, loading: true, role: null })
        renderAtDashboard()

        expect(screen.queryByText('Dashboard Content')).not.toBeInTheDocument()
        expect(screen.queryByText(/Login Page/)).not.toBeInTheDocument()
    })

    it('redirects to /login (not expired) when there was never a session', () => {
        useAuth.mockReturnValue({ session: null, loading: false, role: null })
        renderAtDashboard()

        expect(screen.getByText('Login Page (expired: false)')).toBeInTheDocument()
    })

    it('redirects to / when the role is not in allowedRoles', () => {
        useAuth.mockReturnValue({ session: { user: { id: 1 } }, loading: false, role: 'FRONT_DESK' })
        renderAtDashboard(['ADMIN'])

        expect(screen.getByText('Home Page')).toBeInTheDocument()
    })

    it('renders the outlet and WhatsNewGate for an authenticated, allowed role', () => {
        useAuth.mockReturnValue({ session: { user: { id: 1 } }, loading: false, role: 'ADMIN' })
        renderAtDashboard(['ADMIN'])

        expect(screen.getByText('Dashboard Content')).toBeInTheDocument()
        expect(screen.getByText('WhatsNewGate')).toBeInTheDocument()
    })

    it('allows any role through when allowedRoles is not specified', () => {
        useAuth.mockReturnValue({ session: { user: { id: 1 } }, loading: false, role: 'FRONT_DESK' })
        renderAtDashboard(undefined)

        expect(screen.getByText('Dashboard Content')).toBeInTheDocument()
    })

    it('marks the redirect as "expired" once a previously-present session disappears (idle timeout / token expiry)', () => {
        useAuth.mockReturnValue({ session: { user: { id: 1 } }, loading: false, role: 'ADMIN' })
        const { rerender } = renderAtDashboard(['ADMIN'])
        expect(screen.getByText('Dashboard Content')).toBeInTheDocument()

        useAuth.mockReturnValue({ session: null, loading: false, role: null })
        rerender(
            <MemoryRouter initialEntries={['/dashboard']}>
                <Routes>
                    <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
                        <Route path="/dashboard" element={<div>Dashboard Content</div>} />
                    </Route>
                    <Route path="/login" element={<LoginStub />} />
                    <Route path="/" element={<div>Home Page</div>} />
                </Routes>
            </MemoryRouter>
        )

        expect(screen.getByText('Login Page (expired: true)')).toBeInTheDocument()
    })
})
