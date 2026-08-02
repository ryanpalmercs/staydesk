import { createContext, useContext, useEffect, useState } from 'react'
import { supabase } from '../lib/supabase'
import { getEmployee } from '../api/employeeApi'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
    const [session, setSession] = useState(undefined)
    const [isSystemAdmin, setIsSystemAdmin] = useState(false)

    useEffect(() => {
        supabase.auth.getSession().then(({ data: { session } }) => setSession(session))
        const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
            setSession(session)
        })
        return () => subscription.unsubscribe()
    }, [])

    useEffect(() => {
        const role = session?.user?.app_metadata?.role
        const userId = session?.user?.id

        if (role !== 'ADMIN' || !userId) {
            setIsSystemAdmin(false)
            return
        }

        getEmployee(userId)
            .then(() => setIsSystemAdmin(false))
            .catch(err => setIsSystemAdmin(err.response?.status === 404))
    }, [session])

    return (
        <AuthContext.Provider value={{
            session,
            user: session?.user ?? null,
            role: session?.user?.app_metadata?.role ?? null,
            isSystemAdmin,
            loading: session === undefined,
            signOut: () => supabase.auth.signOut()
        }}>
            {children}
        </AuthContext.Provider>
    )
}

export const useAuth = () => useContext(AuthContext)