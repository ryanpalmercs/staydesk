import { useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { supabase } from '../lib/supabase'
import { useAuth } from '../contexts/AuthContext'

export default function LoginPage() {
    const { session, loading } = useAuth()
    const [mode, setMode] = useState('password')
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState(null)
    const [magicLinkSent, setMagicLinkSent] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const location = useLocation()
    const sessionExpired = location.state?.expired

    if (!loading && session) {
        return <Navigate to="/" replace />
    }

    async function handleSubmit(e) {
        e.preventDefault()
        setError(null)
        setSubmitting(true)
        if (mode === 'password') {
            const { error } = await supabase.auth.signInWithPassword({ email, password })

            if (error) {
                setError(error.message)
            }
        } else {
            const { error } = await supabase.auth.signInWithOtp({ email })

            if (error) {
                setError(error.message)
            } else {
                setMagicLinkSent(true)
            }
        }
        setSubmitting(false)
    }

    if (magicLinkSent) {
        return (
            <div className="min-h-screen bg-cream flex items-center justify-center">
                <div className="bg-white rounded-lg shadow-md w-full max-w-sm p-8 text-center">
                    <div className="nav-logo mb-6">Stay<span>Desk</span></div>
                    <p className="text-sm text-charcoal mb-1">Check your email</p>
                    <p className="text-sm text-charcoal/60 mb-6">We sent a sign-in link to <strong>{email}</strong>.</p>
                    <button className="text-sm text-rust underline" onClick={() => { setMagicLinkSent(false); setMode('password') }}>
                        Back to sign in
                    </button>
                </div>
            </div>
        )
    }

    return (
        <div className="min-h-screen bg-cream flex items-center justify-center">
            <div className="bg-white rounded-lg shadow-md w-full max-w-sm p-8">
                <div className="nav-logo text-center mb-8" style={{ color: 'var(--color-brown)' }}>Stay<span>Desk</span></div>
                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    {sessionExpired && <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded px-3 py-2">Your sesion expired. Please sign in again.</p>}

                    {error && <p className="text-sm text-red-600">{error}</p>}

                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-charcoal">Email</label>
                        <input type="email" value={email} onChange={e => setEmail(e.target.value)} required className="border border-charcoal/20 rounded px-3 py-2 text-sm" />
                    </div>

                    {mode === 'password' && (
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium text-charcoal">Password</label>
                            <input type="password" value={password} onChange={e => setPassword(e.target.value)} required className="border border-charcoal/20 rounded px-3 py-2 text-sm" />
                        </div>
                    )}

                    <button type="submit" disabled={submitting} className="bg-charcoal text-white text-sm font-medium py-2 rounded hover:bg-charcoal/90 disabled:opacity-50">
                        {mode === 'password' ? 'Sign in' : 'Send magic link'}
                    </button>

                    <button type="button" className="text-sm text-rust underline" onClick={() => { setMode(mode === 'password' ? 'magic' : 'password'); setError(null) }}>
                        {mode === 'password' ? 'Use a magic link instead' : 'Use password instead'}
                    </button>
                </form>
            </div>
        </div>
    )
}