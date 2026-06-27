# StayDesk — Planning

## Overview
Lightweight motel management app for small independent properties. Built for Martin House — 27 rooms.

## Rate Structure (Martin House)
| Rate Type | Guests | Price |
|-----------|--------|-------|
| Nightly | 1 guest | $64.17 |
| Nightly | 2 guests (double) | $75.00 |
| Weekly (5 nights) | 1 guest | $230.50 |
| Weekly (7 nights) | 1 guest | $322.70 |
| Weekly (5 nights) | 2 guests | $246.80 |
| Weekly (7 nights) | 2 guests | $345.00 |
| Weekly (5 nights) | 3 guests | $299.00 |
| Weekly (7 nights) | 3 guests | $418.60 |

## MVP Scope

### Guest Management
- [ ] Room inventory (room_number, type, nightly_rate, status)
- [ ] Guest records (name, email, phone)
- [ ] Reservations (guest, room, dates, status, check-in/out timestamps)
- [ ] Check-in / check-out actions (front desk)
- [ ] Smart lock integration (Sifely) — generate and deliver time-limited door code at check-in, revoke at check-out
- [ ] Folio per reservation (line items: nightly charges, taxes, extras)
- [ ] Room hold at check-in via Stripe (PaymentIntent with capture_method: manual — estimated stay amount)
- [ ] Incidental hold at check-in via Stripe (separate PaymentIntent — flat buffer amount, admin-configurable)
- [ ] Final capture at checkout against both holds — settle actuals, release remainder
- [ ] SMS notifications (Twilio)

### Employee & Payroll
- [ ] Employee records (name, role, pay_rate, hire_date, active)
- [ ] Clock-in / clock-out time tracking (built into StayDesk)
- [ ] Manual hours entry as fallback
- [ ] Timesheet views (by employee, by pay period)
- [ ] Timesheet export (CSV + PDF) — CPA-friendly format

### Reports
- [ ] Occupancy report by date range (day / week / month / custom)
- [ ] Revenue report by date range
- [ ] Average nightly rate
- [ ] Occupancy by room (which rooms book most/least)
- [ ] Period comparison (this month vs last month)
- [ ] Breakdown by guest count tier (1 guest / 2 guest / 3 guest rates)
- [ ] Export as CSV and PDF (CPA-friendly)

### General
- [ ] Admin dashboard (today's arrivals, departures, occupancy)
- [ ] Single-tenant (one property)
- [ ] Auth via Supabase (JWT validated at Spring Security filter layer)
- [ ] Stripe webhook handler (payment_intent.succeeded → mark folio paid)

### Compliance
- [ ] Guest PII encryption at rest
- [ ] Missouri guest registration data collection (address, vehicle info)
- [ ] Data retention policy and automated purge (90-day minimum)
- [ ] Breach notification plan (Missouri Rev. Stat. § 407.1500)
- [ ] Privacy policy for guest-facing pages
- [ ] Terms of service
- [ ] Pre-launch Missouri attorney review

## Data Model Summary
| Table | Key Columns |
|-------|-------------|
| `rooms` | room_number, type, nightly_rate, status, sifely_lock_id |
| `guests` | first_name, last_name, email, phone |
| `reservations` | guest_id, room_id, check_in_date, check_out_date, status, checked_in_at, checked_out_at, door_code, door_code_pwd_id |
| `folios` | reservation_id, status (open/closed), total, paid_at |
| `folio_items` | folio_id, description, amount, type (charge/tax/payment) |
| `extras` | name, price, active |
| `employee_types` | name, auth_role, active |
| `employees` | first_name, last_name, email, username, employee_type_id, pay_rate, hire_date, active |
| `time_entries` | employee_id, clock_in, clock_out, date, hours, notes |

All tables include `created_at` and `updated_at` audit columns.

## API Surface (planned)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/POST | /rooms | List / create rooms |
| PUT/DELETE | /rooms/{id} | Update / remove room |
| GET/POST | /reservations | List / create reservations |
| PUT/DELETE | /reservations/{id} | Modify / cancel reservation |
| POST | /reservations/{id}/check-in | Check guest in |
| POST | /reservations/{id}/check-out | Check guest out |
| GET | /folios/{id} | View folio |
| POST | /folios/{id}/items | Add line item |
| POST | /folios/{id}/pay | Capture Stripe payment |
| GET/POST | /api/admin/employees | List / create employees (admin only) |
| PUT | /api/admin/employees/{id} | Update employee (admin only) |
| PUT | /api/admin/employees/{id}/role | Update employee role (admin only) |
| DELETE | /api/admin/employees/{id} | Deactivate employee (admin only) |
| GET/POST | /api/admin/employee-types | List / create employee types (admin only) |
| GET/POST | /employees/{id}/hours | Get / log time entries |
| POST | /employees/{id}/clock-in | Clock in |
| POST | /employees/{id}/clock-out | Clock out |
| PUT/DELETE | /hours/{id} | Edit / delete time entry |
| GET | /payroll/timesheets/export | Export timesheets (CSV/PDF) |
| POST | /api/auth/employee/login | Employee username+PIN login (public) |
| POST | /webhooks/stripe | Stripe event handler |

## Open Questions (pending client requirements)
- How many employees / payroll frequency?
- Confirm Brookfield/Linn County lodging tax: currently assuming combined sales tax only (8.73% — 4.225% state + 1.75% county + 2.25% city + 0.5% special district), no separate transient guest tax found. Needs a call to city hall to confirm.
- Extras catalog: what line items (towels, late checkout, etc.) and pricing should be seeded?

## Phase 2 (Post-Launch)

### Remote Check-In
- [ ] Guest-facing token-based check-in link (via SMS)
- [ ] Guest confirms details, signs agreements, provides card info ahead of arrival
- [ ] Card holds (room + incidental) captured at remote check-in instead of front desk
- [ ] Reservation status: pre-checked-in → checked-in
- [ ] Door code delivered digitally on completion
- [ ] Front desk notified on completion

### Payroll Integration
- [ ] QuickBooks API integration for full in-app payroll processing

### Email Notifications
- [ ] Transactional email for reservation confirmations, receipts, and check-in instructions

### Channel Manager Integration
- [ ] Integrate with Channex API (REST/JSON) for OTA distribution
- [ ] Two-way sync of room availability and rates across Booking.com, Airbnb, Expedia, and 50+ OTAs
- [ ] Automatic reservation ingestion from OTA bookings into StayDesk
- [ ] Overbooking prevention via real-time inventory sync
- Note: Direct API access to Booking.com/Expedia requires a channel manager middleman — Channex is the recommended option (modern REST API, 2-4 week integration estimate)

### Customer-Facing Booking Site
- [ ] Public booking site for direct online reservations

## Decisions Log
| Date | Decision | Reason |
|------|----------|--------|
| 2026-06-11 | Separate repo from Eternatel | Different domain, avoid scope creep |
| 2026-06-11 | Single-tenant to start | One property, keep it simple |
| 2026-06-11 | Built-in time clock over Homebase integration | Homebase has no public API; keeps stack self-contained |
| 2026-06-11 | Timesheet export (CSV/PDF) for CPA | Covers payroll workflow until QuickBooks integration ships in Phase 2 |
| 2026-06-11 | Two separate holds at check-in (room + incidentals) | Industry standard; clean separation between room charges and incidentals; both settle or release at checkout |
| 2026-06-11 | 45-day timeline | Expanded from 28 days; restaurant removed from scope (burned down) |
| 2026-06-16 | Single blended lodging tax rate (8.73%) instead of itemized state/county/city components | Simpler folio display; rate is configurable via `app.lodging-tax-rate` since it's specific to Brookfield/Linn County |
| 2026-06-16 | Extras priced from a predefined catalog, not free-form staff entry | Keeps pricing consistent; catalog seed data still pending client input |
| 2026-06-16 | Folio line items can only be added while status is OPEN | Forces late/forgotten charges through a separate process instead of editing a closed folio |
| 2026-06-19 | Employee login via username + PIN proxied through Supabase email auth | Owner not tech-savvy; employees need desk-friendly login; real emails kept for payroll/comms |
| 2026-06-19 | Five auth tiers (admin/manager/front_desk/housekeeping/employee) mapped from client-managed employee types | Allows owner to add new staff categories without code changes; auth logic stays code-enforced |
| 2026-06-26 | Smart locks (Sifely S) replace keycards | Martin House chose Sifely; door codes generated at check-in, SMS'd to guest, revoked at check-out |
| 2026-06-26 | Remote check-in deferred to Phase 2 | Out of v1.0.0 contract scope |
| 2026-06-26 | QuickBooks over Gusto for payroll integration | Client preference; deferred to Phase 2 |
| 2026-06-26 | SMS only in Phase 1, email in Phase 2 | Unblocks launch; Twilio already integrated |
