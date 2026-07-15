import { useEffect, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { supabase } from '../lib/supabase'
import { useAuth } from '../contexts/AuthContext'
import api from '../api/baseApi'

export default function LoginPage() {
    const { session, loading } = useAuth()
    // TODO: Implement mode at a later time if employee emails are a thing

    // const [mode, setMode] = useState('password')
    const [identifier, setIdentifier] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState(null)
    // TODO: Implement mode at a later time if employee emails are a thing

    // const [magicLinkSent, setMagicLinkSent] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const location = useLocation()
    const sessionExpired = location.state?.expired || new URLSearchParams(location.search).get('expired') === 'true'

    useEffect(() => {
        if (sessionExpired) {
            window.history.replaceState({}, '')
        }
    }, [])

    if (!loading && session) {
        const role = session.user?.app_metadata?.role
        return <Navigate to={role === 'HOUSEKEEPING' ? '/housekeeping' : '/'} replace />
    }

    async function handleSubmit(e) {
        e.preventDefault()
        setError(null)
        setSubmitting(true)

        // TODO: Implement mode at a later time if employee emails are a thing
        // if (mode === 'password') {
        if (identifier.includes('@')) {
            const { error } = await supabase.auth.signInWithPassword({ email: identifier, password })

            if (error) {
                setError(error.message)
            }
        } else {
            try {
                const res = await api.post('/auth/employee/login', { username: identifier, pin: password })

                console.log(res.data)

                await supabase.auth.setSession({ access_token: res.data.access_token, refresh_token: res.data.refresh_token })
            } catch (err) {
                setError('Invalid username or PIN')
                console.log(err)
            }
        }

        // TODO: Implement mode at a later time if employee emails are a thing

        // } else {
        //     const { error } = await supabase.auth.signInWithOtp({ email: identifier })

        //     if (error) {
        //         setError(error.message)
        //     } else {
        //         setMagicLinkSent(true)
        //     }
        // }
        setSubmitting(false)
    }

    // TODO: Implement mode at a later time if employee emails are a thing

    // if (magicLinkSent) {
    //     return (
    //         <div className="min-h-screen bg-cream flex items-center justify-center">
    //             <div className="bg-white rounded-lg shadow-md w-full max-w-sm p-8 text-center">
    //                 <div className="nav-logo mb-6">Stay<span>Desk</span></div>
    //                 <p className="text-sm text-black mb-1">Check your email</p>
    //                 <p className="text-sm text-black/60 mb-6">We sent a sign-in link to <strong>{identifier}</strong>.</p>
    //                 <button className="text-sm text-green underline" onClick={() => { setMagicLinkSent(false); setMode('password') }}>
    //                     Back to sign in
    //                 </button>
    //             </div>
    //         </div>
    //     )
    // }

    return (
        <div className="min-h-screen bg-cream flex items-center justify-center">
            <div className="bg-white rounded-lg shadow-md w-full max-w-sm p-8">
                <div className="nav-logo text-center mb-8" style={{ color: 'var(--color-black)' }}>Stay<span>Desk</span></div>
                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    {sessionExpired && <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded px-3 py-2">Your session expired. Please sign in again.</p>}

                    {error && <p className="text-sm text-red-600">{error}</p>}

                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-black">Username or Email</label>
                        <input type="text" value={identifier} onChange={e => setIdentifier(e.target.value)} required className="border border-black/20 rounded px-3 py-2 text-sm" />
                    </div>

                    {/* {mode === 'password' && ( */}
                    <div className="flex flex-col gap-1">
                        <label className="text-sm font-medium text-black">Password or PIN</label>
                        <input type="password" value={password} onChange={e => setPassword(e.target.value)} required className="border border-black/20 rounded px-3 py-2 text-sm" />
                    </div>
                    {/* )} */}

                    <button type="submit" disabled={submitting} className="bg-black text-white text-sm font-medium py-2 rounded hover:bg-black/90 disabled:opacity-50">
                        {/* {mode === 'password' ? 'Sign in' : 'Send magic link'} */}
                        Sign in
                    </button>

                    {/* <button type="button" className="text-sm text-green underline" onClick={() => { setMode(mode === 'password' ? 'magic' : 'password'); setError(null) }}>
                        {mode === 'password' ? 'Use a magic link instead' : 'Use password instead'}
                    </button> */}
                </form>
            </div>
        </div>
    )
}