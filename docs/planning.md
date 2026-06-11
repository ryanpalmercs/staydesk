# Staydesk — Planning

## Overview
Lightweight motel management app for small independent properties (~10-15 rooms).

## MVP Scope

### Guest Management
- [ ] Room inventory (number, type, rate)
- [ ] Reservations (dates, guest info, room assignment)
- [ ] Check-in / check-out
- [ ] Basic folio (nightly charges, taxes, extras)
- [ ] Payment capture via Stripe

### Payroll
- [ ] Employee records
- [ ] Hours tracking (manual entry)
- [ ] Gusto API integration

### General
- [ ] Admin dashboard
- [ ] Single-tenant (one property)
- [ ] Auth via Supabase

## Open Questions (pending client requirements)
- Walk-in only or online reservations too?
- Card on file vs pay at checkout?
- How many employees / payroll frequency?
- Any existing tools in use?

## Decisions Log
| Date | Decision | Reason |
|------|----------|--------|
| 2026-06-11 | Separate repo from Eternatel | Different domain, avoid scope creep |
| 2026-06-11 | Gusto over ADP for payroll integration | Friendlier API, better fit for small employer |
| 2026-06-11 | Single-tenant to start | One property, keep it simple |
