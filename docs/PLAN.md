# MNP Practicum — Approach & Plan

> My recommendation on what to build first, how to sequence it, and how to lay out the repo.
> Scope from the brief: submit/accept/reject number-porting requests between operators
> (Vodafone, Orange, Etisalat), with mocked auth via an `organization` header.
>
> The PDF's two diagrams are extracted to `docs/diagrams/`
> ([flow](diagrams/porting-flow-diagram.png), [state](diagrams/state-diagram.png)) —
> both **confirm** the design below.

---

## TL;DR

**Build the backend domain + DB design first, as one working vertical slice (create a porting
request end-to-end), then grow outward.** Don't start with scaffolding everything at once, and
don't start with the frontend. The reviewer explicitly grades *clean code, API/DB design, naming,
and sensible use of Spring* — so the domain model and the state machine are where you win or lose.

---

## What to create *first* (in order)

1. **Git repo + `dev/initial` branch + `.gitignore` + README skeleton.** The brief grades the git
   workflow, so set it up before the first commit, not after.
2. **Write down the domain model & state machine** (see below). 10 minutes here saves hours.
3. **Design the API contract + MySQL schema** (Flyway migrations, see
   [DB_SCHEMA.md](DB_SCHEMA.md)). Lock naming conventions now.
4. **Backend scaffold** via Spring Initializr: Spring Web, Validation, Spring Data JPA, MySQL
   Driver, Flyway Migration, Actuator, Configuration Processor, Testcontainers. Then add
   `springdoc-openapi` (Swagger UI) and `java-uuid-generator` to `pom.xml` by hand.
5. **First vertical slice:** `POST /porting-requests` working against real MySQL — controller →
   service → repository → DB. Everything else is a variation on this.

---

## Recommended build sequence (phased)

| Phase | Deliverable |
|-------|-------------|
| 1 | Domain model, DB schema, repo + branch, backend scaffold |
| 2 | `organization` header → operator resolution (mocked auth filter/interceptor) |
| 3 | Create porting request (+ all validation rules) |
| 4 | Donor accept / reject (with authz: only donor) |
| 5 | List requests (visibility rules) + number-status lookup |
| 6 | Scheduled timeout-cancellation job (2 min) |
| 7 | Tests (unit for rules, slice/integration for API) |
| 8 | Angular: create-order form + list page |
| 9 | Dockerfile + docker-compose (app + MySQL), README run/test docs |
| 10 | Merge `dev/initial` → `master`, tag `v1.0.0-dev`, delete remote branch |

---

## Domain model (the core)

- **Operator** — DB table seeded with the 3 operators + their number ranges. Prefer a *table* over
  an enum: the brief lists them as "data" and seeding ranges in SQL demonstrates DB-design care.
- **PortingRequest** — `id`, `phoneNumber`, `recipientOperatorId`, `donorOperatorId`, `status`,
  `createdAt`, `resolvedAt`. One phone number per request (single field, not a list).
- **PortedNumber** (or equivalent) — `phoneNumber` → `currentOperatorId`. Lets you answer "current
  holder if the number is ported." Updated when a request is **accepted**.

**Status state machine** (matches the PDF's state diagram exactly):

```
 Start ──invalid──► (rejected at validation, no record)
   │ valid
   ▼
          donor accepts ─► ACCEPTED (terminal)  → updates PortedNumber
PENDING ──┤ donor rejects ─► REJECTED (terminal)
          └ 2 min elapse  ─► CANCELED (terminal, by scheduler)
```

**Flow is asynchronous** (from the PDF's sequence diagram): Recipient → System submits the request
and gets an immediate **Ack** (record created `PENDING`); the Donor responds later with
accept/reject; a System background task cancels timed-out pending requests. So submit and
accept/reject are separate calls, not one blocking exchange.

---

## Key design decisions (these are what the reviewer looks for)

- **Donor is derived, not supplied.** Recipient = the `organization` header operator. Donor = the
  number's *current holder* (look up `PortedNumber`, else fall back to the operator owning that
  number range). Reject if recipient == donor (can't port a number you already hold).
- **Mock auth as a `OncePerRequestFilter`/interceptor** that reads `organization`, resolves the
  Operator, stashes it in a request-scoped bean, and 401/403s on missing/unknown values.
- **"No duplicate pending" rule:** enforce in-service inside the transaction
  (`existsByPhoneNumberAndStatus(PENDING)`); optionally back it with a DB safeguard.
- **Visibility rule** on list: return requests where `recipient = me OR donor = me OR
  status = ACCEPTED`. Push this into the repository query, not Java-side filtering.
- **Schema validation** via Jakarta Bean Validation (`@Valid`, phone-number `@Pattern`), surfaced
  through a single `@RestControllerAdvice` returning consistent error bodies.
- **DB ownership:** Flyway owns the schema. The app runs the migrations in
  `src/main/resources/db/migration/` on startup, against Docker Compose's MySQL and the
  Testcontainers MySQL alike, so there's one path for both. These SQL files are the "MySQL script"
  the brief asks for. JPA runs with `ddl-auto: validate` so Hibernate never silently reshapes the
  schema.
- **API docs:** springdoc-openapi serves Swagger UI at `/swagger-ui.html`. Declare the
  `organization` header once for every operation (an `OpenApiCustomizer`), so reviewers can try
  each endpoint as any operator straight from the page.
- **Scheduler:** `@Scheduled` job flips `PENDING` → `CANCELED` where
  `createdAt < now - 2min`. Make the timeout configurable in `application.yaml`.

---

## Decisions made

Agreed while writing [DB_SCHEMA.md](DB_SCHEMA.md) and [USER_STORIES.md](USER_STORIES.md).

**API & errors**
- **Status codes:** `400` schema, `401` missing or unknown header, `403` visible but not allowed,
  `404` missing or not visible, `409` state conflict (a pending request exists, or the request
  isn't `PENDING`), `422` valid schema but breaks a business rule (number outside every range,
  or the recipient already holds it).
- **A third party acting on a request gets `404`, not `403`.** It can't see pending requests,
  so the API doesn't reveal that the request exists.
- **Unknown JSON fields return `400`** (`FAIL_ON_UNKNOWN_PROPERTIES` on). Clients can't set
  `donor` or `status`.
- **`organization` values match exactly.** `Vodafone` with a capital V returns `401`.
- **Ids in URIs and responses are UUIDs.** Porting request ids are UUID v7 (`BINARY(16)`), so
  they can't be guessed and don't show volume. Operators are exposed by `code`, never by DB id.
  A path id that isn't a UUID returns `400`. Visibility checks still do the real access
  control.

**Domain**
- **Status spelling is `CANCELED`**, matching the PDF's state diagram.
- **A number ported back to its original operator counts as not ported.** Ported means the
  current holder isn't the range owner.
- **Re-porting is allowed.** The donor is whoever holds the number now.
- **Numbers outside every operator range are rejected** (`422` on submit, `404` on status
  lookup).

**Database**
- **One range per operator**, stored on `operators`. No separate ranges table.
- **`ported_numbers` keeps only the final state** (`phone_number`, `current_operator_id`). Port
  details come from the latest `ACCEPTED` request.
- **No `version` or `updated_at` columns.** The accept/cancel race is handled by conditional
  updates (`WHERE status = 'PENDING'`), and a request changes only once.
- **Duplicate pending requests are blocked by a unique generated column** as well as the
  service check.

**Testing**
- **Inject a `Clock`** (or use a few-second timeout) so the timeout job can be tested without
  waiting 2 minutes.
- **Acceptance criteria have IDs** (`US-R3-AC4`) that tests reference in `@DisplayName`.

---

## Suggested folder structure (monorepo)

```
mnp-project/
├── README.md                 # build / run / test instructions
├── docker-compose.yml        # app + mysql
├── .gitignore
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/fourgtss/mnp/
│       │   ├── MnpBackendApplication.java
│       │   ├── config/          # scheduling, web, auth filter
│       │   ├── common/          # error handling, exceptions, constants
│       │   ├── operator/        # Operator entity + repo + range lookup
│       │   ├── porting/
│       │   │   ├── api/          # controllers + request/response DTOs
│       │   │   ├── domain/       # PortingRequest, Status
│       │   │   ├── repository/
│       │   │   ├── service/      # business rules + state transitions
│       │   │   └── scheduler/    # timeout canceller
│       │   └── phonenumber/      # status / current-holder lookup
│       ├── main/resources/
│       │   ├── application.yaml
│       │   └── db/migration/
│       │       ├── V1__create_schema.sql
│       │       └── V2__seed_operators.sql
│       └── test/java/...         # unit + web-slice + integration tests
├── frontend/                 # Angular 20+
│   └── src/app/
│       ├── core/                 # http, organization-header interceptor
│       └── porting/              # create-order form + list page
└── docs/
    ├── PLAN.md               # this file
    ├── DB_SCHEMA.md
    ├── USER_STORIES.md
    └── diagrams/             # flow + state diagrams from the PDF
```

---

## API sketch

| Method | Path | Who | Purpose |
|--------|------|-----|---------|
| POST | `/porting-requests` | recipient | Submit a request for one number |
| POST | `/porting-requests/{id}/accept` | donor only | Accept (ports the number) |
| POST | `/porting-requests/{id}/reject` | donor only | Reject |
| GET | `/porting-requests` | any | List, filtered by visibility rules |
| GET | `/porting-requests/{id}` | any | One request; `404` if the caller may not see it |
| GET | `/phone-numbers/{phoneNumber}` | any | Current status + current holder |

All requests require the `organization` header.

---

## Don't-forget checklist (easy marks)

- [ ] Commit on `dev/initial` from the start; small, meaningful commits.
- [ ] `docker-compose up` works clean from a fresh clone (healthcheck/`depends_on` so app waits for MySQL).
- [ ] README covers build, run, **and test** in Docker.
- [ ] Consistent naming across API, DTOs, DB columns (snake_case in DB, camelCase in JSON).
- [ ] Meaningful tests for the business rules (duplicate-pending, donor-only authz, timeout, visibility).
- [ ] Merge to `master`, tag `v1.0.0-dev`, delete the remote `dev/initial` branch.
- [ ] Email repo link + short description to mostafa.hares@4gtss.com.
