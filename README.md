# MNP — Mobile Number Portability

A recipient mobile network operator submits a request to port a phone number; the donor operator
accepts or rejects it. Pending requests that the donor doesn't answer in time are canceled
automatically.

- **Backend:** Spring Boot 4.1, Java 21, MySQL 8.4, Flyway
- **Frontend:** Angular 20+ *(coming)*

## Repository layout

| Path | Contents |
|------|----------|
| `backend/` | Spring Boot service |
| `frontend/` | Angular app *(coming)* |
| `docs/` | [Plan](docs/PLAN.md), [DB schema](docs/DB_SCHEMA.md), [user stories](docs/USER_STORIES.md), diagrams |

## Prerequisites

- JDK 21
- Docker (for Docker Compose and the Testcontainers-based tests)

## Run locally (development)

```bash
docker compose up -d mysql           # MySQL 8.4 on localhost:3306
cd backend
./mvnw spring-boot:run               # Flyway creates the schema on startup
curl localhost:8080/actuator/health  # {"status":"UP",...}
```

Tests start their own MySQL with Testcontainers, so they only need Docker running:

```bash
cd backend
./mvnw test
```

Database settings default to the Compose values and can be overridden with `DB_HOST`,
`DB_PORT`, `DB_NAME`, `DB_USERNAME` and `DB_PASSWORD`.

## Build, run and test with Docker

*To be completed when the app container is added to Docker Compose.*
