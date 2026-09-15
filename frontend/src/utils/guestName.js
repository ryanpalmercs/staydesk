// Uppercase everywhere a guest's name is displayed, matching front-desk/ID-matching convention.
// This is display-only - the underlying firstName/lastName/name data is left exactly as staff
// entered it. Prefers the backend's computed `name` (business-entity aware); falls back to a
// manual join for any caller passing a partial guest object without it.
export function formatGuestName(guest) {
    if (!guest) {
        return ''
    }

    const name = guest.name ?? (guest.lastName ? `${guest.firstName} ${guest.lastName}` : guest.firstName ?? '')
    return name.toUpperCase()
}
