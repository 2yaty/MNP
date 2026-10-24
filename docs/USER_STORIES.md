# MNP — User Stories & Acceptance Criteria

> Companion to [PLAN.md](PLAN.md) and [DB_SCHEMA.md](DB_SCHEMA.md).
> Every acceptance criterion (AC) has an ID, so each one can map to at least one test
> (for example `@DisplayName("US-R1-AC2 ...")`).

---

## Roles

| Role | Who | How it's identified |
|------|-----|---------------------|
| **Recipient** | The operator that wants to take over a number | `organization` header on the submit call |
| **Donor** | The operator that holds the number when the request is made | Stored on the request at creation |
| **Third party** | Any operator that is neither recipient nor donor of a request | `organization` header ≠ both |
| **Operator agent** | Any operator, looking up a number | Valid `organization` header |
| **System** | The background timeout job | No header; runs on a schedule |

The same operator is recipient on some requests, donor on others, and a third party on the rest.
The role is always **per request**.

---

## Response conventions (used by the ACs)

| Status | When |
|--------|------|
| `201 Created` | Request submitted |
| `200 OK` | Read, accept, reject |
| `400 Bad Request` | Body or path fails schema validation (missing field, bad format, malformed JSON, unknown field) |
| `401 Unauthorized` | `organization` header missing or unknown |
| `403 Forbidden` | Caller can see the request but isn't allowed to act on it |
| `404 Not Found` | Request doesn't exist, or the caller isn't allowed to see it |
| `409 Conflict` | Breaks the request state: a pending request already exists, or the request isn't `PENDING` anymore |
| `422 Unprocessable Entity` | Valid schema but breaks a business rule: number outside every range, or the recipient already holds the number |

## Test fixtures

| Operator | Header | Range | Sample number |
|----------|--------|-------|---------------|
| Vodafone | `vodafone` | 01000000000–01099999999 | `01012345678` |
| Etisalat | `etisalat` | 01100000000–01199999999 | `01112345678` |
| Orange | `orange` | 01200000000–01299999999 | `01212345678` |

Out-of-range sample: `01512345678`. Timeout: 2 minutes (overridden to a few seconds, or driven
by an injected `Clock`, in tests).

---

## Mocked authentication (all roles)

### US-AUTH-1 — Identify the calling operator
**As** any operator, **I want** to be identified by my `organization` header, **so that** the
system knows which operator I am acting for.

| AC | Given / When | Then |
|----|--------------|------|
| AC1 | `organization: vodafone` on any endpoint | Request is processed as Vodafone |
| AC2 | No `organization` header | `401` |
| AC3 | `organization: we` (unknown value) | `401` |
| AC4 | `organization: Vodafone` (wrong case) | `401`; values match exactly |

---

## Recipient

### US-R1 — Submit a porting request
**As** a recipient, **I want** to submit a porting request for one phone number, **so that** the
donor can accept or reject it.

| AC | Given / When | Then |
|----|--------------|------|
| AC1 | Orange submits `01012345678` | `201`; status `PENDING`; recipient = Orange; donor = Vodafone; `createdAt` set; `resolvedAt` null |
| AC2 | Orange submits a valid request | The response returns right away without waiting for the donor (async Ack, as in the flow diagram) |
| AC3 | The client sends a `donor` or `status` field | `400`; the donor is always derived and the status always starts at `PENDING` |

### US-R2 — Request schema validation
**As** a recipient, **I want** invalid requests rejected with a clear error, **so that** only
well-formed requests enter the system.

| AC | Given / When | Then |
|----|--------------|------|
| AC1 | Body has no `phoneNumber` | `400`, error names the field |
| AC2 | `phoneNumber` = `0101234567` (10 digits) | `400` |
| AC3 | `phoneNumber` = `0101234567a` / `+201012345678` / `21012345678` | `400` |
| AC4 | `phoneNumber` is a list (`["01012345678", "01112345678"]`) | `400`; one number per request |
| AC5 | Empty body or malformed JSON | `400` |
| AC6 | Any `400` | Nothing is stored |

### US-R3 — Business rules on submit
**As** a recipient, **I want** requests that break the porting rules refused, **so that** the
system's data stays consistent.

| AC | Given / When | Then |
|----|--------------|------|
| AC1 | A `PENDING` request exists for `01012345678`; anyone submits it again | `409` |
| AC2 | Same as AC1, but the second submit comes from a **different** recipient | `409` |
| AC3 | The previous request for the number is `REJECTED`, `CANCELED` or `ACCEPTED` | New request allowed, `201` |
| AC4 | Two submits for the same number arrive at the same time | Exactly one `201`, the other `409` (DB unique constraint) |
| AC5 | Vodafone submits `01012345678` (it already holds it) | `422` |
| AC6 | `01512345678` (outside every range) | `422` |

### US-R4 — Request a number that was ported before
**As** a recipient, **I want** the donor to be whoever holds the number now, **so that** numbers
that were ported before can be ported again.

| AC | Given / When | Then |
|----|--------------|------|
| AC1 | `01012345678` was ported Vodafone → Orange; Etisalat submits it | `201`; donor = **Orange** |
| AC2 | Same number; Vodafone submits it (porting it back home) | `201`; donor = Orange |
| AC3 | Same number; Orange submits it | `422`; Orange already holds it |

### US-R5 — View my requests as recipient
**As** a recipient, **I want** to see all my requests in every status, **so that** I can track
them.

| AC | Given / When | Then |
|----|--------------|------|
| AC1 | Orange has requests in each status as recipient | All of them are listed |
| AC2 | Orange fetches one of them by id | `200` whatever its status |

---

## Donor

### US-D1 — Accept a porting request
**As** a donor, **I want** to accept a pending request, **so that** the number moves to the
recipient.

| AC | Given / When | Then |
|----|--------------|------|
| AC1 | Vodafone accepts a `PENDING` request where it is donor | `200`; status `ACCEPTED`; `resolvedAt` set |
| AC2 | After AC1 | Number status shows holder = recipient, `ported = true` |
| AC3 | After AC1 | The request is visible to third parties (see US-T1) |

### US-D2 — Reject a porting request
**As** a donor, **I want** to reject a pending request, **so that** the number stays with me.

| AC | Given / When | Then |
|----|--------------|------|
| AC1 | Vodafone rejects a `PENDING` request where it is donor | `200`; status `REJECTED`; `resolvedAt` set |
| AC2 | After AC1 | Holder unchanged (still Vodafone) |
| AC3 | After AC1 | A new request for the number is allowed (US-R3-AC3) |

### US-D3 — Only the donor can respond
**As** a donor, **I want** no other operator to be able to answer requests addressed to me,
**so that** nobody can approve a port on my behalf.

| AC | Given / When | Then |
|----|--------------|------|
| AC1 | The **recipient** tries to accept or reject its own request | `403`; status unchanged |
| AC2 | A **third party** tries to accept or reject | `404` (they can't see a pending request); status unchanged |

### US-D4 — Only pending requests can be answered
| AC | Given / When | Then |
|----|--------------|------|
| AC1 | Donor accepts or rejects a request that is `ACCEPTED`, `REJECTED` or `CANCELED` | `409`; nothing changes |
| AC2 | Donor accepts a well-formed UUID that doesn't exist | `404` |
| AC5 | Request id in the path isn't a UUID (`/api/porting-requests/42`) | `400` |
| AC3 | The timeout job cancels the request, then the donor accepts | `409`; status stays `CANCELED`; holder unchanged |
| AC4 | Donor accepts while the job cancels at the same moment | Exactly one wins; final state is either `ACCEPTED` + ported, or `CANCELED` + not ported, never a mix |

### US-D5 — View requests addressed to me
| AC | Given / When | Then |
|----|--------------|------|
| AC1 | Vodafone is donor on requests in each status | All of them are listed |

---

## Third party

### US-T1 — See only accepted requests of others
**As** an operator that isn't part of a request, **I want** to see only completed ports, **so
that** I know where numbers moved without seeing other operators' open business.

| AC | Given / When | Then |
|----|--------------|------|
| AC1 | Vodafone → Orange requests exist in every status; Etisalat lists requests | Only the `ACCEPTED` one is returned |
| AC2 | Etisalat fetches the `ACCEPTED` request by id | `200` |
| AC3 | Etisalat fetches a `PENDING`, `REJECTED` or `CANCELED` request by id | `404` |

---

## Operator agent (any operator)

### US-A1 — Look up a number's status and current holder
**As** an operator agent, **I want** to get a number's status and current holder, **so that** I
know who serves it now.

| AC | Given / When | Then |
|----|--------------|------|
| AC1 | `01012345678`, never ported | `200`; holder = Vodafone; `ported = false` |
| AC2 | `01012345678` ported to Orange | `200`; holder = Orange; `ported = true` |
| AC3 | Ported to Orange, then back to Vodafone | holder = Vodafone; `ported = false` (holder = range owner) |
| AC4 | Has a `PENDING` or `REJECTED` request only | Holder unchanged; `ported = false` |
| AC5 | Bad format (`0101`) | `400` |
| AC6 | `01512345678` (outside every range) | `404` |
| AC7 | Any operator can call it, including ones not involved | Same answer for all callers |

### US-A2 — List requests
| AC | Given / When | Then |
|----|--------------|------|
| AC1 | Any operator lists requests | Gets the requests where it is recipient **or** donor, plus everyone's `ACCEPTED` ones |
| AC2 | — | Each request appears once, newest first |
| AC3 | `?page=2&size=5` | That page of the list, with `page`, `size`, `totalItems` and `totalPages`. Defaults: page `0`, size `20` |
| AC4 | `page` below 0, `size` below 1 or above 100, or not a number | `400` |

---

## System (timeout job)

### US-S1 — Cancel pending requests that time out
**As** the system, **I want** pending requests canceled once they pass the timeout, **so that**
numbers aren't blocked forever by donors who don't respond.

| AC | Given / When | Then |
|----|--------------|------|
| AC1 | `PENDING` request older than the timeout; job runs | Status `CANCELED`; `resolvedAt` set |
| AC2 | `PENDING` request younger than the timeout; job runs | Still `PENDING` |
| AC3 | `ACCEPTED`, `REJECTED` or `CANCELED` requests of any age; job runs | Unchanged |
| AC4 | After a cancel | Holder unchanged; a new request for the number is allowed |
| AC5 | Timeout set in `application.yaml` | The job uses that value (2 minutes by default) |
| AC6 | Job runs twice in a row | Second run changes nothing |

---

## Frontend (Angular)

### US-UI-1 — Create a porting request
| AC | Given / When | Then |
|----|--------------|------|
| AC1 | User picks the acting operator and enters a number | The form sends `POST` with the `organization` header |
| AC2 | Invalid number entered | Error shown before sending; submit disabled |
| AC3 | Server returns `409` / `422` / `400` | The server's message is shown |
| AC4 | Success | Confirmation shown, with the new request's status `PENDING` |

### US-UI-2 — View requests
| AC | Given / When | Then |
|----|--------------|------|
| AC1 | User picks the acting operator | The list shows what that operator may see (US-A2) |
| AC2 | — | Each row shows number, recipient, donor, status, created and resolved times |

---

## Which test level covers what

| Level | Tool | Covers |
|-------|------|--------|
| Unit | JUnit 5 + Mockito | Donor derivation, business rules, error mapping (US-R3, R4, D3, D4-AC1/2, A1) |
| Web slice | `@WebMvcTest` | Header auth and schema validation (US-AUTH-1, US-R2, US-A1-AC5) |
| Integration | `@SpringBootTest` + Testcontainers MySQL | Full flows, visibility, DB constraints, races, timeout job (US-R1, R3-AC4, D1, D2, D4-AC3/4, T1, A2, S1) |
| UI | Angular component tests | US-UI-1, US-UI-2 |
