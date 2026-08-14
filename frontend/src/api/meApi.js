import api from "./baseApi";

export function getCurrentUser() {
    return api.get('/me')
}