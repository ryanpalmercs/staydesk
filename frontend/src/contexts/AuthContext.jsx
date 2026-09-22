import { createContext, useContext, useEffect, useState } from 'react'
import { supabase } from '../lib/supabase'
import { getEmployee } from '../api/employeeApi'
import { getCurrentUser } from '../api/meApi'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
    const [session, setSession] = useState(undefined)
    const [isSystemAdmin, setIsSystemAdmin] = useState(false)
    const [displayName, setDisplayName] = useState("")

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

    useEffect(() => {
        const userId = session?.user?.id

        if (!userId) {
            setDisplayName("")
            return
        }

        getCurrentUser()
            .then(res => setDisplayName(res.data.displayName))
            .catch(err => setDisplayName(""))
    }, [session])

    return (
        <AuthContext.Provider value={{
            session,
            user: session?.user ?? null,
            role: session?.user?.app_metadata?.role ?? null,
            isSystemAdmin,
            loading: session === undefined,
            displayName,
            signOut: () => supabase.auth.signOut()
        }}>
            {children}
        </AuthContext.Provider>
    )
}

export const useAuth = () => useContext(AuthContext)