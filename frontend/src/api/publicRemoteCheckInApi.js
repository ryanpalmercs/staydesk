import publicApi from './publicApi.js'

export function getRemoteCheckInSummary(token) {
    return publicApi.get(`/public/remote-check-in/${token}`)
}

export function completeRemoteCheckIn(token, incidentalsPaymentMethodId, agreedToTerms) {
    return publicApi.post(`/public/remote-check-in/${token}/complete`, { incidentalsPaymentMethodId, agreedToTerms })
}
