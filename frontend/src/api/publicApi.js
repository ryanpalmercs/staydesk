import axios from 'axios'

// Unauthenticated axios instance for the public booking site. Deliberately does not reuse
// baseApi.js: that instance attaches a Supabase staff token and redirects to /login on 401,
// neither of which is meaningful for an unauthenticated guest-facing request.
const publicApi = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL,
    timeout: 60000
})

export default publicApi
