# Staydesk

Motel management app for Martin House Motel — reservations, check-in/out, guest
folio, card payments, and payroll sync, built for a single 27-room property.

## Modules

- Guest Management (reservations, check-in/out, room inventory, folio)
- Payments (Authorize.net for online/phone capture, Elavon CPI for card-present terminal)
- Payroll (Gusto API integration)

## Stack

- **Backend:** Spring Boot 3.5, PostgreSQL 15, Authorize.net, Elavon CPI, Gusto API
- **Frontend:** React 19
- **Auth:** Supabase
- **CI/CD:** GitHub Actions

## Project Structure

```
staydesk/
├── backend/        # Spring Boot API
├── frontend/       # React 19 UI
└── docs/           # Staff-facing SOPs, training guides, quick reference cards
```

## Prerequisites

- Java 21
- Node 22
- Docker (for local Postgres)

## Getting Started

### Backend

Start local Postgres:

```bash
docker compose up -d
```

This runs Postgres 15 on `localhost:5433` (db `staydesk_dev`, user `postgres` / `local_password`).

Create `backend/src/main/resources/application-local.yml` (gitignored) with the
secrets needed for local dev — Twilio, Sifely, guest PII encryption keys,
Authorize.net, Elavon CPI, and the Supabase service role key. Ask a teammate for
values, or point Authorize.net/Elavon at sandbox credentials.

Run the server:

```bash
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

The API is served on `localhost:8080`.

### Frontend

```bash
cp frontend/src/.env.example frontend/.env.local
```

Fill in `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`, and the Authorize.net
keys in `.env.local`.

```bash
cd frontend
npm install
npm run dev
```

The dev server runs on `https://localhost:5174` (self-signed cert) and proxies
`/api` requests to the backend on port 8080.

## Testing

```bash
cd backend
./gradlew test
```

No frontend test runner is configured yet.

## Branching

`master ← beta ← develop ← feature/*` (or `bugfix/*`). See [CLAUDE.md](CLAUDE.md#branching-strategy) for the full workflow.

## Docs

See [`docs/`](docs/) for staff-facing SOPs, training guides, and quick-reference cards.
