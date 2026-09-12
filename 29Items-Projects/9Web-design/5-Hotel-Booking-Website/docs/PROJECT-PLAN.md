# Hotel Booking Website - Project Plan

## 1.1 Project File Structure

```text
.
├── .github/workflows/ci.yml
├── config/app.config.js
├── docs/
│   ├── ARCHITECTURE.md
│   ├── API.md
│   ├── PROJECT-PLAN.md
│   └── TECH-NOTES.md
├── migrations/001_initial_schema.sql
├── netlify/functions/
│   ├── _lib/
│   │   ├── auth.js
│   │   ├── availabilityService.js
│   │   ├── bookingService.js
│   │   ├── dateUtils.js
│   │   ├── db.js
│   │   ├── errors.js
│   │   ├── guestService.js
│   │   ├── hotelService.js
│   │   ├── response.js
│   │   ├── roomService.js
│   │   ├── serializers.js
│   │   ├── staffUserService.js
│   │   └── validation.js
│   ├── availability.js
│   ├── bookings.js
│   ├── guests.js
│   ├── hotels.js
│   ├── rooms.js
│   └── staff-users.js
├── src/
│   ├── assets/css/styles.css
│   ├── assets/js/main.js
│   ├── components/
│   │   ├── AdminDashboard.js
│   │   ├── BookingForm.js
│   │   ├── HotelMap.js
│   │   └── HotelResults.js
│   ├── data/hotels.json
│   ├── services/bookingApi.js
│   ├── utils/html.js
│   └── index.html
├── tests/
│   ├── e2e/booking.spec.js
│   ├── helpers/testDb.js
│   ├── integration/
│   │   ├── booking-flow.test.js
│   │   └── functions.test.js
│   └── unit/bookingService.test.js
├── docker-compose.yml
├── Dockerfile
├── server.js
└── package.json
```

### Source Code Organization

- `src/index.html`: Main responsive Bootstrap application.
- `src/components`: Vanilla JS UI modules for booking search, results, map display, and staff admin.
- `src/services`: Browser API wrapper for public and staff endpoints.
- `src/utils`: Small frontend safety helpers, including HTML escaping for dynamic rendering.
- `netlify/functions`: Serverless API endpoints for availability and CRUD operations.
- `netlify/functions/_lib`: Database access, validation, serialization, service-layer business rules, and auth helpers.
- `migrations`: PostgreSQL schema, indexes, constraints, and seed data.

### CI/CD Organization

- `.github/workflows/ci.yml`: Installs dependencies, lints, tests, builds, and keeps Netlify deployment hooks ready.
- `netlify.toml`: Static publish directory, functions directory, redirects, and security/cache headers.
- `Dockerfile` and `docker-compose.yml`: Local full-stack runtime for static app, API adapter, and PostgreSQL.

## 1.2 Implementation Checklist

### Phase 1: Foundation

- [x] Finalize visual layout for search, results, booking confirmation, and staff admin views.
- [x] Connect frontend search UI to real availability data.
- [x] Implement persistent PostgreSQL access for Netlify Functions.
- [x] Add validation for booking dates, guest count, room capacity, and duplicate reservations.
- [x] Configure local, Docker, Netlify, and CI environment variables.
- [x] Add unit tests for pricing and booking validation.

### Phase 2: Core Features

- [x] Implement hotel display cards and Swiper gallery.
- [x] Add Leaflet map markers from live hotel/room data.
- [x] Build guest checkout flow with backend confirmation codes.
- [x] Add staff token authorization and role-based API checks.
- [x] Create staff room inventory and booking management screens.
- [x] Add integration tests for booking creation, CRUD, and availability checks.

### Phase 3: Polish & Optimization

- [x] Add loading, empty, and error states across booking/admin UI.
- [x] Configure lazy images and CDN cache headers.
- [x] Improve form semantics, labels, status regions, and responsive tables.
- [x] Keep analytics integration points outside the core logic so events can be added without changing services.
- [x] Configure end-to-end test entry point for the booking page.
- [x] Centralize production error logging and JSON error envelopes.
- [x] Add API pagination, HTTP compression, security headers, XSS escaping, and Docker env hardening.
