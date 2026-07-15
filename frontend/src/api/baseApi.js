import axios from 'axios'
import { supabase } from '../lib/supabase'

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL,
    timeout: 60000
})

api.interceptors.request.use(async config => {
    const { data: { session } } = await supabase.auth.getSession()
    if (session?.access_token) {
        config.headers.Authorization = `Bearer ${session.access_token}`
    }
    return config
})

api.interceptors.response.use(
    res => res,
    err => {
        if (err.response?.status === 401) {
            window.location.replace('/login?expired=true')
        }
        return Promise.reject(err)
    }
)

export default api