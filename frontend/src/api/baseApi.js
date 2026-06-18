import axios from 'axios'

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL,
    timeout: 60000
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