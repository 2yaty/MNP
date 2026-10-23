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

## Build, run and test

*To be completed with the Docker Compose setup.*
