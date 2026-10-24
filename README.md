# MNP — Mobile Number Portability

A recipient mobile network operator submits a request to port a phone number; the donor operator
(whoever holds the number now) accepts or rejects it. Pending requests that the donor doesn't
answer within 2 minutes are canceled automatically.

- **Backend:** Spring Boot 4.1 (Spring MVC on Java 21 virtual threads), JPA, MySQL 8.4, Flyway
- **Frontend:** Angular 21, served by nginx

## Quick start (Docker)

Requires Docker with Compose v2.

```bash
docker compose up --build -d --wait
```

This builds the images, starts MySQL, waits until it accepts connections, then starts the backend
(which creates the schema and seed data with Flyway) and the frontend. `--wait` returns once all three
are healthy. The first build takes a few minutes while Maven and npm download dependencies; later
builds reuse a cache.

| What | Where |
|------|-------|
| **App** | **http://localhost:4200** |
| API | http://localhost:8080/api (also through the app at http://localhost:4200/api) |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| MySQL | `localhost:3306`, database/user/password `mnp` |

In the app, pick the operator you're acting as in the header, submit a request under
**New request**, then switch to the donor to accept or reject it under **Requests**.

```bash
docker compose logs -f backend   # follow the backend logs
docker compose down              # stop (data is kept in the mysql-data volume)
docker compose down -v           # stop and delete the data
```

## Try it

Every API call needs an `organization` header naming the calling operator: `vodafone`, `orange`
or `etisalat` (mocked authentication). In Swagger UI it's a dropdown on each endpoint.

| Operator | Number range |
|----------|--------------|
| Vodafone | 01000000000 – 01099999999 |
| Etisalat | 01100000000 – 01199999999 |
| Orange | 01200000000 – 01299999999 |

```bash
# Orange asks to take over a Vodafone number → 201, status PENDING, donor vodafone
curl -i -X POST localhost:8080/api/porting-requests \
  -H "organization: orange" -H "Content-Type: application/json" \
  -d '{"phoneNumber": "01012345678"}'

# Vodafone (the donor) accepts it; use the id from the response above
curl -X POST localhost:8080/api/porting-requests/<id>/accept -H "organization: vodafone"

# Anyone can look up who serves the number now → currentHolder orange, ported true
curl localhost:8080/api/phone-numbers/01012345678 -H "organization: etisalat"

# List the requests the caller may see, newest first
curl "localhost:8080/api/porting-requests?page=0&size=20" -H "organization: etisalat"
```

| Endpoint | Who | Purpose |
|----------|-----|---------|
| `POST /api/porting-requests` | recipient | Submit a request for one number |
| `POST /api/porting-requests/{id}/accept` | donor | Accept; the number moves to the recipient |
| `POST /api/porting-requests/{id}/reject` | donor | Reject; the number stays |
| `GET /api/porting-requests?page=&size=` | any | Own requests in every status, plus everyone's accepted ones |
| `GET /api/porting-requests/{id}` | any | One request, `404` if the caller may not see it |
| `GET /api/phone-numbers/{phoneNumber}` | any | Range owner, current holder, and whether it's ported |

Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details
(`application/problem+json`): `400` invalid input, `401` missing or unknown `organization`,
`403` not allowed, `404` not found or not visible, `409` state conflict (for example, a pending
request already exists), `422` business rule broken (for example, the number is outside every range).

## Configuration

Set these as environment variables, for example in a `.env` file next to `docker-compose.yml`.

| Variable | Default | Used by |
|----------|---------|---------|
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | `mnp` / `mnp` / `mnp` | MySQL and the backend |
| `DB_ROOT_PASSWORD` | `root` | MySQL |
| `MYSQL_PORT` | `3306` | Host port for MySQL |
| `BACKEND_PORT` | `8080` | Host port for the backend |
| `FRONTEND_PORT` | `4200` | Host port for the app |
| `MNP_PORTING_TIMEOUT` | `2m` | How long a request may stay pending (backend) |
| `MNP_PORTING_EXPIRYCHECKINTERVAL` | `10s` | How often timed-out requests are canceled (backend) |

The two `MNP_PORTING_*` settings aren't wired into `docker-compose.yml`; add them under the
`backend` service's `environment` to override them there.

## Running the tests

Tests run on the host. The backend needs **JDK 21** and a running **Docker**: integration tests
start their own MySQL 8.4 with Testcontainers, so they don't touch the Compose database. The
frontend needs **Node.js 24**.

```bash
cd backend
./mvnw test                  # Windows: mvnw.cmd test

cd frontend
npm ci
npx ng test --watch=false    # Vitest + jsdom
```

Test names reference the acceptance criteria in [docs/USER_STORIES.md](docs/USER_STORIES.md),
for example `US-R3-AC4 concurrent requests for the same number: exactly one wins`.

## Developing without the containers

```bash
docker compose up -d --wait mysql   # MySQL only
cd backend
./mvnw spring-boot:run              # backend on the host, against localhost:3306

cd frontend
npm start                           # app on http://localhost:4200, /api proxied to localhost:8080
```

The backend's database settings default to the Compose values and can be overridden with
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` and `DB_PASSWORD`.

## Repository layout

| Path | Contents |
|------|----------|
| `backend/` | Spring Boot service, Dockerfile, Flyway migrations (`src/main/resources/db/migration`) |
| `frontend/` | Angular app, Dockerfile, nginx config ([frontend README](frontend/README.md)) |
| `docs/` | [Plan and decisions](docs/PLAN.md), [database schema](docs/DB_SCHEMA.md), [user stories](docs/USER_STORIES.md), diagrams |
