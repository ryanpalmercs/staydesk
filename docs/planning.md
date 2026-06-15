# StayDesk — Planning

## Overview
Lightweight motel management app for small independent properties. Built for Martin House — 27 rooms + restaurant.

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
- [ ] Check-in / check-out actions
- [ ] Folio per reservation (line items: nightly charges, taxes, extras)
- [ ] Room hold at check-in via Stripe (PaymentIntent with capture_method: manual — estimated stay amount)
- [ ] Incidental hold at check-in via Stripe (separate PaymentIntent — flat buffer amount, TBD with client, suggested $50-75)
- [ ] Final capture at checkout against both holds — settle actuals, release remainder

### Employee & Payroll
- [ ] Employee records (name, role, pay_rate, hire_date, active)
- [ ] Clock-in / clock-out time tracking (built into StayDesk)
- [ ] Manual hours entry as fallback
- [ ] Timesheet views (by employee, by pay period)
- [ ] Timesheet export (CSV + PDF) — CPA-friendly format
- [ ] Gusto API integration (OAuth 2.0) for full in-app payroll processing

### General
- [ ] Admin dashboard (today's arrivals, departures, occupancy)
- [ ] Single-tenant (one property)
- [ ] Auth via Supabase (JWT validated at Spring Security filter layer)
- [ ] Stripe webhook handler (payment_intent.succeeded → mark folio paid)

## Data Model Summary
| Table | Key Columns |
|-------|-------------|
| `rooms` | room_number, type, nightly_rate, status |
| `guests` | first_name, last_name, email, phone |
| `reservations` | guest_id, room_id, check_in_date, check_out_date, status, checked_in_at, checked_out_at |
| `folios` | reservation_id, status (open/paid), total |
| `folio_items` | folio_id, description, amount, type |
| `employees` | first_name, last_name, role, pay_rate, hire_date, active |
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
| GET/POST | /employees | List / create employees |
| PUT | /employees/{id} | Update employee |
| GET/POST | /employees/{id}/hours | Get / log time entries |
| POST | /employees/{id}/clock-in | Clock in |
| POST | /employees/{id}/clock-out | Clock out |
| PUT/DELETE | /hours/{id} | Edit / delete time entry |
| POST | /payroll/sync | Push hours to Gusto |
| GET | /payroll/timesheets/export | Export timesheets (CSV/PDF) |
| POST | /webhooks/stripe | Stripe event handler |

## Backlog — Post v1.x

### Restaurant & POS
- [ ] Menu item management (categories, items, prices)
- [ ] Order taking and ticket management
- [ ] JavaFX desktop POS terminal (cash drawer support via receipt printer)
- [ ] Charge to room (post restaurant charges directly to guest folio)
- [ ] Restaurant revenue tracking separate from room revenue
- [ ] Kitchen ticket printing

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
| 2026-06-11 | Built-in time clock over Homebase integration | Homebase has no public API; keeps stack self-contained |
| 2026-06-11 | Timesheet export (CSV/PDF) alongside Gusto | Supports CPA workflow if client prefers external payroll processing |
| 2026-06-11 | Two separate holds at check-in (room + incidentals) | Industry standard; clean separation between room charges and incidentals; both settle or release at checkout |
| 2026-06-11 | JavaFX desktop POS for restaurant | Web-based POS can't reliably drive cash drawers; JavaFX has direct hardware access |
| 2026-06-11 | 45-day timeline | Expanded from 28 days to account for restaurant/POS scope |