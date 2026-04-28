# Event RSVP Manager — Design File

---

## 01 — Setup

This design is written before implementation and focuses on correctness, clarity of ownership, and behavior under concurrency. The implementation target is the provided full-stack scaffold:

- **Backend:** Java 17, Spring Boot, Spring MVC, Spring Data JPA
- **Database:** PostgreSQL
- **Frontend:** React 19, TypeScript, Vite

The design assumes a server-rendered source of truth for all business rules, with the frontend acting as a client of backend APIs.

---

## 03 — Problem Statement

Hosts need a simple and reliable way to manage event invitations and responses, while invitees need a frictionless way to RSVP without account creation or manual coordination.

The main problem is not just collecting responses, but enforcing business correctness around:
- capacity limits
- waitlisting
- automatic promotion from waitlist
- locking RSVP changes after event start
- preventing unauthorized access between hosts and invitees

Without an explicit design, the implementation can easily become inconsistent under race conditions, especially when:
- multiple invitees RSVP "Yes" at the same time for the last available seat
- a confirmed attendee drops out and a waitlisted attendee should be promoted
- the host closes or cancels the event while invitees are responding

The system therefore needs a design that prioritizes **correctness of event state and RSVP state** over UI convenience.

---

## 04 — Goals and Non-Goals

### Goals

- Allow a host to create and manage events.
- Allow the host to invite people by email using unique invite links.
- Allow invitees to RSVP as Yes / No / Maybe before the event starts, unless the event is closed or canceled.
- Enforce max-capacity rules when capacity is defined.
- Automatically waitlist additional Yes responses once capacity is full.
- Automatically promote a waitlisted invitee when a confirmed attendee gives up a seat.
- Give the host an up-to-date attendance dashboard with counts and attendee list.
- Make backend state the single source of truth for all business rules.
- Handle concurrency correctly for seat allocation.

### Non-Goals

- Email delivery infrastructure beyond modeling invitations and links.
- Calendar integration.
- Recurring events.
- Guest groups or plus-ones.
- Editing event details after creation. Event fields are fixed at creation time in v1.
- Authentication system for general users beyond host ownership and invite-token access.
- Rich analytics beyond current counts/list view.
- Multi-event organization management in the first version.

---

## 05 — Context and Constraints

### Product Context

This is a small event management flow with two primary roles: **host** and **invitee**. The feature contains both CRUD-style behavior (creating events, sending invites) and state-machine behavior (RSVP transitions, capacity enforcement, event lifecycle).

### Technical Constraints

- Spring Boot + JPA + PostgreSQL on the backend; React + TypeScript on the frontend.
- A monolithic application is appropriate for this task scope.
- PostgreSQL is the consistency boundary for all transactional state.
- Unique invite links imply token-based access for invitees — no account creation required.
- The backend must own all capacity, waitlist, and locking rules; the frontend may not derive authoritative seat allocation.

### UX Constraints

- Invitees must be able to RSVP with minimal friction (no registration required).
- The host dashboard must reflect current backend state without manual recalculation.
- Responses submitted after event start time must be rejected consistently and with a clear business error.

### Design Constraints

A complete design for this task is expected to include:
- Explicit Event and RSVP state machines.
- Named invariants.
- Trust boundaries between host and invitee.
- At least one concurrency scenario covering last-seat contention.

---

## 06 — Facts, Assumptions, and Open Questions

### Facts

- An event has exactly one host.
- Invitees respond through unique links.
- Valid RSVP choices are Yes / No / Maybe.
- Max-capacity is optional.
- If capacity is reached, new Yes responses become waitlisted.
- A waitlisted attendee can be automatically promoted if a confirmed attendee changes to No.
- The host may cancel or close the event at any time.
- RSVP changes are locked after event start time.

### Assumptions

- Each invitation corresponds to exactly one invitee email and one RSVP record.
- The unique invite link contains a high-entropy token that does not expose internal IDs.
- Invitees do not need full user accounts.
- "Live dashboard" means fresh backend state on reload or polling; WebSockets are not required for v1.
- Waitlist promotion follows deterministic FIFO ordering by waitlist timestamp.
- Host identity is established via the `X-Host-Id` request header. The scaffold is responsible for setting this header in v1; no general auth system is implemented.
- CLOSED is terminal in v1. The host cannot reopen a closed event.
- Invite tokens do not expire. The only time-based lock is `currentTime >= startTime`.
- FIFO by `respondedAt` is the sole waitlist ordering in v1. No manual host reordering.
- The host dashboard is readable in any event state including CANCELED. Cancellation does not revoke host read access.
- Once an event is canceled, no further invitee responses are accepted.
- Once an event is closed, no further invitee responses are accepted, even before start time.
- A Maybe response does not consume a capacity slot.

### Open Questions

All scoped open questions for v1 have been resolved. Decisions are recorded in the assumptions above and in §17 Key Design Decisions.

- ~~Can the host edit event details after creation?~~ **Decided:** Out of scope in v1. See §04 Non-Goals.
- ~~Can the host reopen a closed event?~~ **Decided:** No. CLOSED is terminal in v1. Once closed, the host can only cancel; no backward transition exists.
- ~~If an event is canceled, should the dashboard remain visible as read-only?~~ **Decided:** Yes. Host read access is never revoked. Cancellation blocks RSVP writes only.
- ~~Should invite links expire after a certain period?~~ **Decided:** No expiry in v1. The only time-based restriction is `currentTime >= startTime` locking RSVP writes.
- ~~Should a waitlisted invitee see a "Yes (waitlisted)" status or a distinct attendance status?~~ **Decided:** The backend returns `YES_WAITLISTED` directly. The frontend displays this as "You're on the waitlist."
- ~~Should the host be able to manually reorder or manage the waitlist?~~ **Decided:** No. FIFO by `respondedAt` is the only ordering in v1.
- ~~What host authentication model is expected by the scaffold?~~ **Decided:** `X-Host-Id` request header (§11). Backend reads `hostId` exclusively from this header. Requests without it are rejected with 401.
- ~~Should duplicate invites to the same email for the same event be prevented?~~ **Decided:** Yes. Enforced by `UNIQUE(eventId, email)` at the database layer.

Future-version scope questions are recorded as assumption risks in §14 (R-A1–R-A5).

---

## 07 — Actors and Workflows

### Actors

**Host** — The creator and manager of the event. Performs privileged actions.

**Invitee** — A person invited by email who accesses their invitation via a unique tokenized link.

**System** — Backend services and database enforcing all business rules.

### Core Workflows

**Workflow A: Host creates event**
1. Host submits title, description, date/time, location, optional max-capacity.
2. Backend validates input.
3. Backend creates event with host as owner; event starts in OPEN status.
4. Backend returns created event.

**Workflow B: Host invites people**
1. Host submits one or more email addresses.
2. Backend creates invitation records and unique tokens.
3. Backend associates each invitation with the event.
4. Email delivery is out of scope for v1; links are generated and stored.

**Workflow C: Invitee opens unique link**
1. Invitee accesses link containing token.
2. Backend resolves token to invitation and event.
3. Backend verifies token validity and event accessibility.
4. Frontend shows invitation details and current RSVP state.

**Workflow D: Invitee submits RSVP (first response)**
1. Invitee chooses Yes / No / Maybe.
2. Backend checks: event not CANCELED, not CLOSED, current time before event start.
3. Backend derives outcome status:
   - Yes → `YES_CONFIRMED` if seat available (or no capacity set)
   - Yes → `YES_WAITLISTED` if capacity is set and confirmed count equals maxCapacity
   - No → `NO`
   - Maybe → `MAYBE`
4. Backend writes RSVP record with the derived status.
5. No promotion is triggered — a first response never frees a seat.

**Workflow E: Invitee changes RSVP before event start**
1. Invitee reopens unique link and selects a new response.
2. Backend checks: event not CANCELED, not CLOSED, current time before event start.
3. Backend derives the new outcome status using the same rules as Workflow D.
4. Previous RSVP status is replaced by the new outcome.
5. If the previous status was `YES_CONFIRMED` and the new status is `NO` or `MAYBE`, a seat is freed: the backend promotes the next eligible `YES_WAITLISTED` record in FIFO order (by `respondedAt`) within the same transaction.

**Workflow F: Host views dashboard**
1. Host opens event dashboard.
2. Backend returns event details and attendee summary.
3. Counts include: confirmed, waitlisted, no, maybe, pending, total invited.
4. The dashboard is accessible in any event state (OPEN, LOCKED, CLOSED, CANCELED). Cancellation or closure does not revoke host read access.

**Workflow G: Host closes event**
1. Host marks event as closed.
2. Backend updates event status to CLOSED.
3. Future invitee RSVP writes are rejected.

**Workflow H: Host cancels event**
1. Host marks event as canceled.
2. Backend updates event status to CANCELED.
3. Future invitee RSVP writes are rejected.
4. Existing RSVP data remains for audit and display.

---

## 08 — Invariants

These are the correctness properties that must always hold. They take precedence over UI convenience.

| ID  | Invariant |
|-----|-----------|
| I1  | **Single RSVP per invitation** — For a given invitation, there is exactly one current RSVP state. |
| I2  | **Host ownership** — Only the host may invite, close, cancel, or view the host dashboard for an event. |
| I3  | **Invitee scope** — An invitee may only access and modify the RSVP associated with their own unique token. |
| I4  | **Lock-after-start** — No RSVP change is accepted once current time is at or after event start time. |
| I5  | **Close/cancel blocks responses** — No RSVP change is accepted if the event is CLOSED or CANCELED. |
| I6  | **Capacity safety** — If max-capacity is set, the count of CONFIRMED attendees must never exceed it. |
| I7  | **Waitlist-only when full** — If capacity is defined and all confirmed seats are taken, a new Yes cannot become CONFIRMED directly. |
| I8  | **Deterministic promotion** — When a confirmed seat is freed, promotion from the waitlist selects the next eligible attendee by a deterministic ordering rule (FIFO by waitlist timestamp). |
| I9  | **Maybe does not consume a seat** — A MAYBE response is not counted against max-capacity. |
| I10 | **Cancellation is terminal for responses** — Once CANCELED, invitee responses are read-only. |

---

## 09 — First-Pass Architecture

A simple layered monolith is appropriate for v1.

```
React Frontend (TypeScript, Vite)
        |
        v
Spring MVC Controllers
        |
        v
Application / Domain Services
        |
        v
JPA Repositories
        |
        v
PostgreSQL
```

### Backend Components

**Controllers**
- `EventController` — event creation, close, cancel
- `InvitationController` — invite people, token resolution
- `DashboardController` — host attendance summary

**Services**
- `EventService` — event lifecycle rules
- `InvitationService` — token generation and lookup
- `RsvpService` — RSVP transitions, capacity enforcement, waitlist promotion
- `DashboardService` — aggregated attendance data

**Repositories**
- `EventRepository`
- `InvitationRepository`
- `RsvpRepository`

### Rationale

Business logic must not live in controllers. Capacity enforcement, RSVP locking, and waitlist promotion are stateful and cross-entity operations that belong in transactional service-layer methods.

---

## 10 — Data Ownership and State Model

### Data Ownership

**Backend owns:**
- Event lifecycle state
- Invitation tokens
- RSVP truth and history
- Capacity calculations and seat counts
- Waitlist ordering and promotion logic
- All authorization checks

**Frontend owns:**
- Local form state
- Display formatting

The frontend must never derive authoritative seat allocation.

### Event State Machine

**States:**
- `OPEN` — accepting responses
- `CLOSED` — host manually closed responses
- `CANCELED` — host canceled the event
- `LOCKED` — derived, not persisted; applies when `currentTime >= startTime`

**Transitions:**
- `create` → OPEN
- host closes → CLOSED
- host cancels → CANCELED
- `currentTime >= startTime` → responses locked (derived from clock, no background job needed)

**OPEN + LOCKED in API responses:**
When an event's persisted status is `OPEN` but `currentTime >= startTime`, the backend computes an effective status of `LOCKED` and returns it as the `status` field in all API responses. The persisted column remains `OPEN`. Clients must never compute this themselves — the backend always returns the effective status. The host can still close or cancel a LOCKED event.

### RSVP State Machine

**Combined RSVP statuses (v1):**
- `PENDING` — invited, no response yet
- `YES_CONFIRMED` — attending, seat allocated
- `YES_WAITLISTED` — wants to attend, on waitlist
- `NO` — not attending
- `MAYBE` — tentative, no seat allocated

**Allowed transitions:**
- `PENDING` → any response state
- `YES_CONFIRMED` → `NO`, `MAYBE` (frees a seat, may trigger promotion)
- `YES_WAITLISTED` → `YES_CONFIRMED` (via auto-promotion only)
- `MAYBE` → any valid response
- `NO` → any valid response before lock
- Any state → rejected if event is LOCKED, CLOSED, or CANCELED

### Suggested Data Model

**Event**
- `id`, `hostId`, `title`, `description`, `startTime`, `location`, `maxCapacity` (nullable), `status`, `createdAt`, `updatedAt`

**Invitation**
- `id`, `eventId`, `email`, `inviteToken` (unique), `createdAt`

**RSVP**
- `id`, `invitationId`, `status`, `respondedAt`, `version`

`waitlistPosition` is removed from the data model. Waitlist ordering is determined entirely by `respondedAt` (ascending). No field needs to be maintained or resequenced.

---

## 11 — Trust Boundaries and Security Notes

### Where Trust Enters the System

There are exactly two trust entry points:

**Entry point 1 — Host identity**
Host identity is established via an HTTP request header: `X-Host-Id`. The backend reads the `hostId` exclusively from this header — never from the request body or a query parameter. For v1, the scaffold is responsible for setting this header (e.g., from a session, a gateway, or a test fixture). The backend trusts the value present in the header and must reject any request to a host-privileged endpoint that does not include it. No general authentication system is implemented; the header is the sole host identity mechanism in v1.

On every host action that targets an existing event, the backend must verify that the `hostId` from the header matches the `hostId` stored on the event record. A valid header alone is not sufficient — ownership must be confirmed per request.

**Entry point 2 — Invite token**
An invitee's identity and scope are established entirely by the invite token present in the URL. The token is the credential. There is no other invitee identity mechanism. Trust enters only if the token resolves to a valid, active invitation in the database.

All other input from both hosts and invitees is untrusted until validated server-side.

---

### Trust Boundaries

**Boundary A — Host ↔ Backend**

Trust level: authenticated session or equivalent host identity.

Privileged operations that cross this boundary:
- Create an event
- Add invitations to an event
- Close an event
- Cancel an event
- View the host dashboard (full attendee list and counts)

Authorization rule: for every operation on an existing event, the backend must verify that the `hostId` derived from the request matches the `hostId` stored on the event record. Accepting `eventId` alone as proof of authority is not sufficient.

**Boundary B — Invitee ↔ Backend**

Trust level: valid invite token only. No account, no session, no other credential.

Permitted operations once token is validated:
- Read the invitation and current RSVP state
- Submit or change an RSVP response

Scope limit: the token grants access only to the single invitation it maps to. An invitee cannot read another invitee's RSVP, cannot read the full attendee list, and cannot perform any host action. The backend must enforce this by resolving all invitee requests through the token, never through a raw `invitationId` or `eventId` supplied by the client.

**Boundary C — Frontend ↔ Backend**

Trust level: none. All client input is untrusted by default.

The backend must validate at the service layer:
- Date and time fields (valid format, start time in the future for new events)
- Email address format on invitation creation
- RSVP status values (only the allowed enum values)
- Event status transition legality (cannot cancel an already-canceled event, etc.)
- Capacity values (positive integer or null)
- That no business logic result is ever derived from a client-supplied count or state

---

### Tenant Scope

Host ownership is the tenant boundary in v1. Every event is owned by exactly one host. All queries that return event data to a host must be scoped by `hostId`. A host must never be able to read, close, cancel, or invite people to another host's event, even if they know the `eventId`.

There is no cross-tenant sharing model in v1. An invitee token grants access to a single invitation across a single event — it does not grant any broader tenant scope.

---

### Privileged Operations Summary

| Operation | Actor | Authorization check required |
|---|---|---|
| Create event | Host | Host identity must be established |
| Add invitations | Host | Host owns the event |
| Close event | Host | Host owns the event |
| Cancel event | Host | Host owns the event |
| View dashboard (full attendee list) | Host | Host owns the event |
| Read invitation + RSVP state | Invitee | Token resolves to a valid invitation |
| Submit / change RSVP | Invitee | Token resolves to a valid invitation; event rules enforced |

No operation is permitted purely on the basis of knowing an `eventId` or `invitationId`.

---

### Sensitive Data

| Data | Sensitivity | Note |
|---|---|---|
| Invite tokens | High | Must be cryptographically random (high entropy); must not be derived from internal IDs; must not be logged in plain text |
| Invitee email addresses | Medium | Stored on invitation records; must not be exposed to other invitees; must not be leaked in bulk if email is added later |
| Full attendee list | Medium | Visible to host only; invitees must not be able to enumerate other attendees |
| Host identity / credentials | High | Depends on scaffold auth model; must not be accepted as a client-supplied value |
| RSVP status per invitee | Low-medium | Belongs to that invitee and the host; not visible to other invitees |

---

### Security Notes

- Invite tokens must be high-entropy, cryptographically random values — not sequential IDs, not UUIDs derived from timestamps.
- Token lookup must use a constant-time comparison or indexed exact-match query — not a pattern match.
- Host endpoints must verify ownership at the service layer on every request, not just at login time.
- Invitee endpoints must resolve access exclusively through the token; `invitationId` or `eventId` supplied as request parameters do not grant access on their own.
- The full attendee list (names, emails, RSVP statuses) is a host-only view. It must not be returned on invitee-facing endpoints.
- Server-side validation must cover all input fields; client-side validation is convenience only.
- If email delivery is added in a later version, invitation emails must be sent individually — invitee email addresses must not appear in CC or BCC fields visible to others.

---

## 11a — API Contract

All responses are JSON. All request bodies are JSON. The `X-Host-Id` header is required on every host-privileged endpoint.

### Error Response Format

All errors use the same envelope:

```
HTTP <status>
{
  "error": "<ERROR_CODE>",
  "message": "<human-readable description>"
}
```

| Scenario | HTTP status | error code |
|---|---|---|
| Token not found or malformed | 404 | `INVITATION_NOT_FOUND` |
| Event is past start time (LOCKED) | 409 | `EVENT_LOCKED` |
| Event is CLOSED | 409 | `EVENT_CLOSED` |
| Event is CANCELED | 409 | `EVENT_CANCELED` |
| Capacity full (no waitlist slot taken — should not occur; system auto-waitlists) | — | — |
| Host does not own event | 403 | `FORBIDDEN` |
| Missing or invalid request field | 400 | `VALIDATION_ERROR` |
| Duplicate invitation for same email | 409 | `DUPLICATE_INVITATION` |
| `X-Host-Id` header missing | 401 | `UNAUTHORIZED` |

---

### Host Endpoints

All require `X-Host-Id` header. All verify the header value matches the event's `hostId`.

**Create event**
```
POST /api/events
Header: X-Host-Id: <hostId>
Body:   { title, description, startTime (ISO 8601 UTC), location, maxCapacity? }
201:    { id, hostId, title, description, startTime, location, maxCapacity, status, createdAt }
```

**Invite people**
```
POST /api/events/{eventId}/invitations
Header: X-Host-Id: <hostId>
Body:   { emails: ["a@b.com", ...] }
201:    { created: [ { invitationId, email, inviteToken } ], duplicates: [ "already@exists.com" ] }
```
Duplicates are not an error — they are reported in the response. The unique token is returned so the host can distribute the link.

**Close event**
```
POST /api/events/{eventId}/close
Header: X-Host-Id: <hostId>
200:    { id, status: "CLOSED" }
```

**Cancel event**
```
POST /api/events/{eventId}/cancel
Header: X-Host-Id: <hostId>
200:    { id, status: "CANCELED" }
```

**Get dashboard**
```
GET /api/events/{eventId}/dashboard
Header: X-Host-Id: <hostId>
200:    {
          eventId, title, status, startTime,
          counts: { confirmed, waitlisted, no, maybe, pending, total },
          attendees: [
            { invitationId, email, rsvpStatus, respondedAt }
          ]
        }
```
`attendees` includes all invited people regardless of RSVP status. `rsvpStatus` uses the canonical enum values. `respondedAt` is null for PENDING records.

---

### Invitee Endpoints

No `X-Host-Id` header. Access is by token only.

**Get invitation**
```
GET /api/invitations/{token}
200:  {
        invitationId, email,
        event: { id, title, description, startTime, location, status },
        rsvp: { status, respondedAt }
      }
```
`event.status` is the effective status (OPEN / CLOSED / CANCELED / LOCKED — derived by backend before returning). `rsvp.status` is PENDING if no response yet.

**Submit or change RSVP**
```
PUT /api/invitations/{token}/rsvp
Body: { intent: "YES" | "NO" | "MAYBE" }
200:  { rsvpStatus, respondedAt }
```
The client sends `intent` (user's choice). The backend derives `rsvpStatus` (e.g. `YES_CONFIRMED` or `YES_WAITLISTED`). The client never sends a status directly.

---

## 12 — Concurrency and Correctness Notes

Concurrency is a primary design concern for this feature. The invariants most at risk are I6 (capacity safety), I7 (waitlist-only when full), I8 (deterministic promotion), and I5 (close/cancel blocks responses).

---

### Locking Strategy

All RSVP write operations that involve seat allocation must execute inside a single database transaction that acquires a lock on the event row before any read-decide-write sequence. This is the single mechanism that protects invariants I6 and I7.

Sequence inside every RSVP write transaction:
1. Acquire lock on event row (pessimistic, per-event).
2. Read event status and start time — check I4 and I5.
3. Count current `YES_CONFIRMED` records for that event.
4. Apply allocation decision: CONFIRMED if seat available, WAITLISTED if not, based on current count.
5. Write RSVP record.
6. If a seat was freed (prior status was `YES_CONFIRMED`), promote exactly one `YES_WAITLISTED` record in FIFO order.
7. Commit.

No allocation decision may be made outside this transaction. No caller may pre-read the confirmed count and pass it in.

---

### Concurrent Update Scenarios

**Scenario 1 — Two simultaneous Yes RSVPs for the last seat (critical)**

Setup: capacity = 10, 9 currently confirmed, two invitees submit Yes at the same time.

Without locking: both requests read "9 confirmed", both decide CONFIRMED, both write. Final state: 11 confirmed — violates I6.

With event-row lock: one request acquires the lock, counts 9, writes CONFIRMED, commits, releases lock. The second acquires the lock, counts 10, writes WAITLISTED, commits. Final state: 10 confirmed, 1 waitlisted — correct.

**Scenario 2 — Confirmed attendee changes to No while another invitee submits Yes**

Both operations target the same event. Both must go through the event-row lock. The one that commits first sets the post-commit confirmed count. The second reads that post-commit count and decides accordingly. The final confirmed count cannot exceed capacity.

**Scenario 3 — Host closes event while invitee submits RSVP**

Both operations acquire the event-row lock. Whichever commits first determines outcome. If close commits first, the event status is CLOSED when the RSVP transaction reads it — the RSVP write is rejected (I5). If the RSVP commits first, it was valid at commit time. No partial state is possible.

**Scenario 4 — Multiple confirmed attendees change to No simultaneously**

Each change may trigger a promotion. With the event-row lock, promotions are serialized. The FIFO promotion rule (I8) is applied per transaction on the post-commit waitlist state, not based on a snapshot taken before the sibling transaction commits. Each promotion is exactly one record. No double-promotion is possible.

---

### Duplicate Requests

**Same invitee submits Yes twice in quick succession**

Each request resolves to the same invitation and the same RSVP record (I1 — one RSVP per invitation). The second write is an update to the same row, not a second insert. The lock serializes both requests. The final state reflects the last committed write. This is safe as long as the RSVP write is an upsert on the invitation's RSVP row, not an unchecked insert.

**Host sends close or cancel twice**

Both requests acquire the event-row lock. The first transitions the event to CLOSED or CANCELED. The second reads the already-closed/canceled status and should be a no-op or return a deterministic business response — not an error or an invalid second transition.

**Host creates duplicate invitations for the same email**

If `UNIQUE(eventId, email)` is enforced at the database layer, the second insert fails with a constraint violation. This prevents ambiguous dual-RSVP ownership. The service layer must handle this case explicitly and return a meaningful response rather than propagating a raw constraint error.

---

### Stale Reads

**Dashboard read after concurrent RSVP change**

The dashboard is a read-only query. It reflects the committed state at the moment the query runs. Because the frontend polls rather than subscribes, it may briefly show counts that are one write behind. This is acceptable in v1 — eventual visual consistency on polling is a known tradeoff (§15 Alt 3). The backend does not need to do anything special for this; it must only ensure the dashboard query runs outside a write lock and returns committed data.

**Invitee reads their RSVP state after a promotion they triggered**

When a confirmed attendee changes to No, the promotion happens in the same transaction. By the time that transaction commits and returns a response, the promoted invitee's record is already updated. The next time the promoted invitee reloads their page, they will see `YES_CONFIRMED`. No explicit notification mechanism is needed in v1 — this is a polling-model assumption.

---

### Side Effects That Could Violate Invariants

**Promotion outside a transaction (I8 at risk)**

If promotion is triggered asynchronously — for example, via an event after the main transaction commits — it is possible for two seat-freeing changes to both fire a promotion signal before either promotion is applied. The result could be two promotions for one freed seat, breaking I6. Promotion must happen synchronously, inside the same transaction that frees the seat, with the event-row lock held.

**Bypassing the lock for read-then-write in service code (I6, I7 at risk)**

If any code path reads the confirmed count outside a locked transaction and then uses that count to make an allocation decision, I6 and I7 can be violated. This applies to helpers, dashboard computations passed back into write logic, or any lazy-loaded JPA collection count. The count used for allocation must always be computed inside the locked transaction immediately before the write.

**Event status check done before lock acquisition (I5 at risk)**

If the service layer checks event status (OPEN / CLOSED / CANCELED) before acquiring the event-row lock, a concurrent close/cancel can commit between the status check and the RSVP write. The status check must happen after the lock is held.

**RSVP status transition not validated server-side (I1, I4 at risk)**

If the new RSVP status is accepted directly from the client request without checking the allowed transition table, an invitee could submit a status of `YES_CONFIRMED` directly, bypassing capacity logic. The service layer must derive the outcome status itself — it must never accept it as input.

---

### Summary of Correctness Rules

| Rule | Protects | Mechanism |
|---|---|---|
| All RSVP writes acquire event-row lock | I6, I7 | Pessimistic lock in transaction |
| Status and time checks happen after lock | I4, I5 | Order within transaction |
| Confirmed count read inside transaction | I6, I7 | No pre-read count passed in |
| Promotion happens inside same transaction | I8, I6 | Synchronous, not async |
| RSVP outcome derived by backend, not client | I1, I6 | Server-side state machine |
| Duplicate RSVP writes are upserts, not inserts | I1 | Single row per invitation |
| Duplicate invitations rejected at DB layer | I1 | UNIQUE(eventId, email) constraint |

---

## 13 — Scalability and Multi-Tenancy Notes

### Scale Bound (v1)

V1 is scoped to: **≤ 1,000 invited attendees per event, ≤ 100 concurrent events per host, single PostgreSQL instance**. These bounds are not enforced by the application, but they define the regime the design is tested for. Beyond these bounds, a review of the locking strategy, dashboard aggregation, and connection pool sizing is required before proceeding.

### Scalability

V1 does not require distributed architecture. Growth considerations to keep in mind:

- Dashboard queries should be indexed by `eventId`.
- Invitation token lookup should be indexed and unique.
- Confirmed-count and waitlist queries should use efficient aggregate queries rather than loading full collections into memory.
- Polling is acceptable for a "live" dashboard in v1; WebSocket push is a future option.

### Multi-Tenancy

Strict multi-tenancy is not required. Host ownership serves as a lightweight tenant boundary: every event belongs to one host, and all host-visible data must be scoped by that host.

Future concerns (out of scope for v1): organization-level ownership, admin roles, per-tenant rate limits, isolated analytics.

---

## 14 — Risks and Failure Notes

---

### Correctness Risks

These risks directly threaten the named invariants. Any one of them would produce silently wrong state that is hard to detect after the fact.

**R-C1 — Capacity exceeded due to unprotected concurrent writes**
If two Yes RSVPs for the last seat are processed without the event-row lock held for the full read-decide-write sequence, both can confirm. This violates I6. Mitigation: the locking strategy in §12 must be applied without exception. Risk level: high if omitted; eliminated if applied correctly.

**R-C2 — Double promotion when multiple seats free simultaneously**
If promotion is triggered outside the locked transaction — for example as an async side effect — two promotions may fire for one freed seat. This violates I6. Mitigation: promotion must be synchronous and inside the same transaction that frees the seat. Risk level: high if promotion is made async.

**R-C3 — Stale event status check admits an RSVP that should be rejected**
If the status check (`OPEN`, `CLOSED`, `CANCELED`) or the time check (`currentTime < startTime`) is performed before the event-row lock is acquired, a concurrent close or cancel can commit between the check and the write. The result is an RSVP that is stored in a nominally closed or canceled event, violating I5 and I10. Mitigation: both checks must happen after the lock is held.

**R-C4 — RSVP outcome status accepted from client**
If the service accepts a client-supplied RSVP status of `YES_CONFIRMED` or `YES_WAITLISTED` directly, capacity logic is bypassed entirely. This violates I6 and I7. Mitigation: the service must always derive the outcome status itself based on current state; the client supplies only the intent (Yes / No / Maybe).

**R-C5 — Waitlist promotion uses a non-deterministic ordering**
If promotion selects a record without an explicit ORDER BY on waitlist timestamp, database row ordering may vary across queries, making promotion non-deterministic. This violates I8 and produces inconsistent results across deployments or restarts. Mitigation: promotion query must explicitly order by `respondedAt` ascending (FIFO).

**R-C6 — maxCapacity null path not explicitly branched**
If capacity enforcement code does not explicitly check for a null `maxCapacity` before counting confirmed seats, it may throw a null reference error or silently apply a capacity of zero. Mitigation: every capacity and waitlist code path must branch on `maxCapacity == null` and skip enforcement when null.

---

### Dependency Risks

These risks come from external components the design relies on.

**R-D1 — PostgreSQL clock used inconsistently for lock-after-start**
Lock-after-start is derived from `currentTime >= startTime`. If the application server clock and the PostgreSQL server clock diverge, the lock boundary is inconsistent between the service layer check and any database-level time functions. Mitigation: use a single time source — either always read the current time from the application server, or always read it from the database within the transaction. Do not mix.

**R-D2 — JPA lazy loading defeats lock**
If the event entity is loaded lazily or through a cache before the locked query runs, the locked read may return a stale snapshot. JPA first-level cache can return a cached entity even after a `SELECT ... FOR UPDATE`. Mitigation: the locked load must use a query that explicitly bypasses the cache and acquires the lock at the database level (e.g., `PESSIMISTIC_WRITE` lock mode on a fresh load within the transaction).

**R-D3 — DDL auto-update silently misses constraints**
`ddl-auto: update` in `application.yml` will not add new constraints (such as `UNIQUE(eventId, email)`) to an already-existing table if the column exists but the constraint does not. A migration run in development may appear to work while the constraint is absent in another environment. Mitigation: explicit schema migrations should replace `ddl-auto: update` before any shared or production deployment.

---

### Operational Risks

These risks arise from how the system runs rather than from logic errors.

**R-O1 — Invite token logged in application or access logs**
Invite tokens appear in URL paths. Many application servers and reverse proxies log full request URLs by default. A logged token is a leaked credential — anyone with log access can impersonate an invitee. Mitigation: configure log patterns to redact or omit token values from URL logs before any non-local deployment.

**R-O2 — Clock skew causes premature or delayed RSVP lock**
If the application server clock drifts, the lock-after-start check fires at the wrong time. Invitees may be locked out before the event starts, or allowed to RSVP after it starts. Mitigation: NTP synchronization on the server; storing `startTime` in UTC and comparing against UTC server time.

**R-O3 — Long-running transaction blocks other RSVP writes**
The event-row pessimistic lock serializes all RSVP writes per event. If one transaction is slow — due to I/O, a deadlock with another query, or a slow JPA flush — all other RSVP writes for that event are queued. For v1 scale this is acceptable, but a transaction that does not complete must eventually time out and roll back rather than hold the lock indefinitely. Mitigation: set a reasonable transaction timeout.

---

### Assumption Failures

These are assumptions recorded in §06 that, if wrong, would require design changes rather than just fixes.

**R-A1 — Assumption: invitees do not need accounts**
If the product later requires invitees to have persistent identities — to see past RSVPs, receive notifications, or belong to a contact list — the token-only access model breaks down. The data model (Invitation → token) would need to be extended with an optional user reference. This is a structural change to the access boundary, not a patch.

**R-A2 — Assumption: waitlist is FIFO by timestamp**
If the host needs to manually reorder the waitlist or assign priority, the FIFO-by-timestamp assumption is wrong. There is no stored `waitlistPosition` — ordering is entirely derived from `respondedAt`. Supporting manual reordering would require adding a managed sequence field, a new host endpoint, and changes to the promotion logic.

**R-A3 — Assumption: "live dashboard" means polling**
If stakeholders interpret "live" as real-time push (under one second of latency), polling is not sufficient. WebSocket or SSE infrastructure would be needed. This is an out-of-scope assumption (§15 Alt 3) but is listed here because it is the most likely assumption to be challenged after a demo.

**R-A4 — Assumption: a Maybe does not consume a seat**
If the product rules are revised so that Maybe responses reserve a tentative seat, the RSVP state machine changes: Maybe would need to participate in capacity counting, and the confirmed count calculation would change. I9 would be removed or modified. This is not a small patch — it affects the allocation logic in `RsvpService` and the dashboard counts.

**R-A5 — Assumption: host identity is handled by the scaffold** *(Resolved)*
The v1 mechanism is the `X-Host-Id` request header (§11). The scaffold is responsible for setting this header. All host-privileged endpoints reject requests that omit it with 401. No further open question.

---

### Failure Modes and Required Responses

| Failure | Required system response |
|---|---|
| RSVP rejected because event is locked, closed, or canceled | Deterministic business error returned to client; no partial write |
| Promotion fails mid-transaction | Full transaction rollback; no freed seat without a corresponding promotion attempt |
| `maxCapacity` is null | Waitlist and capacity logic skipped entirely; all Yes responses go to CONFIRMED |
| Duplicate invite for same email on same event | Rejected at service or database layer with a meaningful error; not a raw constraint violation |
| Event-row lock times out | Transaction rolled back; caller receives a retriable error |
| Token not found or malformed | 404 or equivalent; no information leaked about whether an event or invitation exists |

---

## 15 — Alternatives and Tradeoffs

The existing alternatives below (Alt 1–4) cover component-level choices. The three alternatives that follow (Alt A–C) are realistic full design directions that differ from the proposed design at a structural level.

---

### Alt 1: Combined RSVP Status vs Split Choice/Allocation Fields

| | Combined status (proposed) | Split model |
|---|---|---|
| Correctness | Allocation outcome is embedded in one field; no desync possible between two fields | Two fields can disagree if a transition updates one but not the other |
| Complexity | Simpler queries; one enum covers intent and system outcome | More expressive; each concept has its own field |
| Future changeability | Adding intent-independent logic (e.g. "show as Maybe but hold a seat") requires a redesign | Easier to add per-dimension logic later |
| Operational burden | Low | Low-medium |

**Decision:** Use combined status in v1. If the product needs to distinguish user intent from seat allocation independently, migrate to split fields at that point.

---

### Alt 2: Derived Lock vs Persisted STARTED Status

| | Derived from clock (proposed) | Persisted STARTED status |
|---|---|---|
| Correctness | Always consistent with clock; no transition can be missed | A missed scheduler run leaves status stale; write-path checks may diverge from persisted status |
| Complexity | Time check on every write path; no scheduler needed | Requires a background job or lazy-transition logic |
| Operational burden | None beyond clock synchronization (R-O2) | Scheduler failure is a new failure mode |
| Future changeability | Trivial — change the comparison condition | Requires a migration when the scheduler logic changes |

**Decision:** Derive lock-after-start from `currentTime >= startTime`. Do not persist a STARTED status.

---

### Alt 3: Polling Dashboard vs WebSocket Live Updates

| | Polling (proposed) | WebSockets / SSE |
|---|---|---|
| Correctness | Reflects committed state on each poll; no sync issues | Requires careful reconciliation if a push event is missed |
| Complexity | Minimal — a plain GET on each refresh interval | Requires persistent connection management on both sides |
| Scalability | Each poll is a stateless request; horizontally scalable | Stateful connections add per-node memory pressure |
| Operational burden | None | Connection lifecycle, reconnect handling, potential load-balancer configuration |

**Decision:** Use polling in v1. Upgrade to SSE or WebSocket if the product specifically requires sub-second dashboard latency.

---

### Alt 4: Pessimistic Locking vs Optimistic Locking for Seat Allocation

| | Pessimistic row lock (proposed) | Optimistic locking with retries |
|---|---|---|
| Correctness | Guaranteed serialization; no lost-update risk | Correct if retries are bounded and complete cleanly; easier to get wrong under contention |
| Complexity | Straightforward inside a single transaction | Retry logic adds conditional branching; version mismatch handling must be explicit |
| Scalability | Serializes all RSVP writes per event; acceptable for small events | Higher throughput under low contention; degrades under high contention |
| Operational burden | Long-held locks need a transaction timeout (R-O3) | Retry storms under high contention add unpredictable latency |

**Decision:** Use pessimistic row-level locking per event in v1. The concurrency level per event is small enough that serialization is not a throughput problem.

---

### Alt A: Separate Capacity Service vs Inline Capacity Logic in RsvpService

**Proposed design:** Capacity counting, seat allocation, and waitlist promotion all live inside `RsvpService` as a single transactional operation.

**Alternative:** Extract a dedicated `CapacityService` that owns all seat-counting and waitlist logic, called by `RsvpService`.

| Dimension | Proposed (inline) | Alternative (CapacityService) |
|---|---|---|
| Correctness | All logic in one transactional method; no cross-service call within a transaction | Same correctness if `CapacityService` is called within the same transaction; broken if called outside |
| Complexity | One class to read and test | Additional abstraction; more indirection; risk of calling capacity logic outside the locked context |
| Future changeability | Harder to reuse capacity logic if it is needed elsewhere | Easier to reuse and test capacity logic in isolation |
| Operational burden | None | None, if boundaries are respected |

**Decision:** Keep capacity logic inline in `RsvpService` for v1. The main risk of extraction is that the capacity read ends up outside the locked transaction boundary, which breaks invariants I6 and I7 (R-C2, R-C3). Extraction is safe only if the boundary is explicitly documented and enforced.

---

### Alt B: Token-Only Invitee Access vs Account-Based Invitee Identity

**Proposed design:** Invitees have no accounts. Access is entirely via a high-entropy token in the URL. The token is the only credential.

**Alternative:** Invitees authenticate with a lightweight account (email + magic link or password) and their RSVP is tied to their account identity rather than to an opaque token.

| Dimension | Proposed (token-only) | Alternative (account-based) |
|---|---|---|
| Correctness | Token grants exactly one RSVP scope; no account conflicts possible | Account must be verified to match the invited email; mismatches create ambiguous ownership |
| Complexity | Simple — token lookup is a single indexed query | Account creation, verification, and session management are additional flows |
| Scalability | Stateless; each request carries its own credential | Sessions or JWTs add per-request overhead |
| Operational burden | Token logging is a security risk (R-O1) | Password resets, account merges, and email verification are new operational concerns |
| Future changeability | Upgrading to account-based access requires linking accounts to existing invitations — a data migration | Already on account model; adding features like history or notifications is natural |
| Assumption dependency | Depends on assumption in §06: invitees do not need persistent identity (R-A1) | Removes R-A1 as a risk; adds R-A5-equivalent risk around account auth implementation |

**Decision:** Use token-only access in v1. The scope is bounded by the task brief: frictionless RSVP without account creation. If persistent invitee identity becomes a requirement, the Invitation table already has an `email` field that can be linked to an account record in a future migration.

---

### Alt C: Monolith with Service Layer vs Domain-Driven Bounded Contexts

**Proposed design:** A single layered monolith with a controller → service → repository stack. Business logic lives in service classes. All components share the same database.

**Alternative:** Model the system as two bounded contexts — Event Management (host domain) and RSVP Response (invitee domain) — with explicit interfaces between them, even if deployed as a single process.

| Dimension | Proposed (layered monolith) | Alternative (bounded contexts) |
|---|---|---|
| Correctness | All state lives in one database; cross-entity transactions are straightforward | Context boundaries require careful definition; shared-database transactions across contexts are either coupled or made eventually consistent |
| Complexity | Low — standard layered architecture | Moderate — explicit interfaces, anti-corruption layers, and event-passing between contexts |
| Scalability | Not independently scalable per context | Each context can be scaled or extracted independently in the future |
| Operational burden | Single deployment unit | Still a single deployment unit in v1, but with internal structure overhead |
| Future changeability | Refactoring into contexts later is possible but requires discipline | Context boundaries are already explicit; extraction to separate services is more mechanical |

**Decision:** Use the layered monolith for v1. The feature scope does not justify bounded-context complexity. However, the service layer boundaries (`EventService`, `InvitationService`, `RsvpService`, `DashboardService`) are already aligned with what bounded context separation would look like — this does not foreclose the option later.

---

## 16 — Rollout and Migration Notes

### Context

This feature is greenfield inside an existing scaffold. There is no legacy data to migrate and no existing users to protect during rollout. The primary rollout concerns are: safe schema introduction, correct sequencing of backend before frontend, and making sure correctness-critical logic is verified before the full feature is exposed.

---

### Schema Migration

**No legacy migration is needed.** Three new tables are introduced: `events`, `invitations`, `rsvps`. They do not touch any existing tables in the scaffold.

Critical schema details that must be applied on first creation, not added later:

| Constraint | Table | Reason |
|---|---|---|
| `UNIQUE(event_id, email)` | `invitations` | Prevents duplicate invites to the same person; enforces I1 indirectly |
| `UNIQUE(invite_token)` | `invitations` | Token must be globally unique; required for token-based access (Boundary B, §11) |
| `NOT NULL` on `host_id`, `title`, `start_time`, `status` | `events` | Core event fields must never be null |
| `NOT NULL` on `event_id`, `email`, `invite_token` | `invitations` | Invitation without an event or contact point is invalid |
| `NOT NULL` on `invitation_id`, `status` | `rsvps` | RSVP without a parent invitation or a status is invalid |
| Index on `invitations.invite_token` | `invitations` | Token lookup is on every invitee request; must be fast |
| Index on `rsvps.invitation_id` | `rsvps` | Joined on every RSVP read; must be indexed |
| Index on `rsvps.status` filtered to `YES_CONFIRMED` per `event_id` | `rsvps` | Confirmed-count query runs inside every RSVP write transaction; must not be a full table scan |

**`ddl-auto: update` is not safe for constraint introduction.** It will create new columns but will not add constraints to already-existing tables. All constraints above must be part of the initial `CREATE TABLE` statements. If the schema is iterated during development, the safest approach is to drop and recreate all three tables rather than rely on `update` to patch them.

---

### Release Sequencing

The feature must be released in layers. Each layer must be independently verifiable before the next is started.

**Layer 1 — Schema only**
Apply the three `CREATE TABLE` statements with all constraints and indexes. Verify with a database client that tables, constraints, and indexes exist exactly as designed. No application code yet.

**Layer 2 — Backend core logic (no endpoints exposed)**
Implement entities, repositories, and service-layer rules: `EventService`, `InvitationService`, `RsvpService`, `DashboardService`. Write unit and integration tests for:
- the last-seat concurrency scenario (§12 Scenario 1)
- lock-after-start rejection
- close and cancel blocking RSVP writes
- FIFO promotion when a confirmed attendee changes to No
- null `maxCapacity` bypass

Do not expose any HTTP endpoints until this layer is verified.

**Layer 3 — Backend endpoints**
Implement and expose controllers. Verify each endpoint manually with a REST client before moving to the frontend. Verify that host endpoints reject requests for events the caller does not own. Verify that invitee endpoints reject malformed or unknown tokens.

**Layer 4 — Frontend**
Implement and connect host and invitee UI flows. At this point the backend is the stable surface; the frontend is a consumer, not a source of truth.

**Layer 5 — End-to-end verification**
Test the full flow: create event → invite → RSVP → dashboard. Verify the last-seat scenario through the UI. Verify that the dashboard reflects updated counts after an RSVP change.

---

### Backward Compatibility

There are no existing API consumers and no existing data, so backward compatibility is not a concern for this initial release.

If the API surface is published and later extended (e.g. adding optional fields to event creation, or adding a reopen endpoint), the following rules apply:
- New optional request fields must not be required by old clients.
- New response fields are additive and safe.
- Status enum values must not be renamed or removed; new values may be added only if clients handle unknown values gracefully.

---

### Feature Flags

No feature flags are needed for this initial release. The entire feature is new and there are no existing users to gate.

If a partial rollout is needed in a future context (e.g. enabling the feature for specific host accounts first), the natural flag boundary is at the event creation endpoint. Disabling that endpoint prevents any new events from being created while leaving existing events and RSVPs accessible.

---

### Rollback

Because all three tables are new and additive, rollback is straightforward:

- Drop `rsvps`, `invitations`, `events` tables in that dependency order.
- Remove the backend controllers and services.
- Remove the frontend flows.

There is no change to existing tables, so rollback does not affect anything currently in the database.

**Rollback window closes** as soon as any real events or RSVPs are created by real users. After that point, dropping the tables destroys user data and rollback requires a data backup strategy rather than a schema drop.

---

### Operationally Sensitive Points During Rollout

**Invite token logging (R-O1):** Before any invitee-facing endpoint is deployed, confirm that the application log configuration does not log full request URLs. Invite tokens appear in URL paths. A single log entry leaks a credential.

**Clock synchronization (R-O2):** Lock-after-start depends on the application server clock. Verify that the server is NTP-synchronized and that `startTime` is stored and compared in UTC before the first real event is created.

**Transaction timeout (R-O3):** The pessimistic event-row lock must have a bounded hold time. Verify that a database-level or JPA-level transaction timeout is configured before exposing the RSVP write endpoint under any load.

**Schema constraints applied before data exists:** The `UNIQUE(event_id, email)` and `UNIQUE(invite_token)` constraints are most safely applied when the tables are empty. If for any reason schema creation and constraint application are done in separate steps, the constraint addition must happen before any invitation data is written.

---

## 17 — Complete Design Draft

---

### Problem

Hosts need a reliable way to create events, invite people, and track attendance responses. Invitees need a frictionless way to RSVP without creating an account.

The core difficulty is not data collection — it is enforcing correctness under concurrent access: multiple invitees competing for the last seat, a host closing an event while responses are in flight, and a confirmed attendee dropping out while a waitlisted invitee should be promoted. The design must prioritize state correctness over UI convenience.

---

### Scope

**In scope:** Event creation and lifecycle management, invitation and token generation, RSVP submission and changes with capacity enforcement, waitlist and automatic promotion, host dashboard, RSVP locking after event start.

**Explicitly out of scope:** Email delivery infrastructure, calendar integration, recurring events, general user authentication, editing events after creation, rich analytics, multi-event organization management.

---

### Actors and Their Boundaries

**Host** — creates and manages events. All host actions are privileged and require verified ownership of the target event. The host sees the full attendee list. No invitee action can reach host-privileged operations.

**Invitee** — accesses a single invitation via a unique token in a URL. The token is the only credential. No account is needed. An invitee can only read and modify the RSVP tied to their own token. They cannot see the full attendee list.

**System** — the backend and PostgreSQL database. All business rules are enforced here. The frontend is a display client only.

These two access paths — host ownership and invitee token — are completely separate and must never share an authorization mechanism.

---

### Architecture

A single layered monolith. No distributed services, no background workers, no message queue.

```
React Frontend  (TypeScript, Vite — display only)
       │  HTTP / JSON
       ▼
Spring MVC Controllers  (routing and input validation only)
       │
       ▼
Application Services  (all business rules enforced here)
       │
       ▼
JPA Repositories
       │
       ▼
PostgreSQL  (single consistency boundary)
```

**Backend components and their responsibilities:**

| Component | Owns |
|---|---|
| `EventController` | Accepts create, close, cancel requests; validates input shape; delegates to service |
| `InvitationController` | Accepts invite submissions and token-based lookups; delegates to service |
| `DashboardController` | Serves host attendance summary; read-only |
| `EventService` | Event lifecycle transitions; host ownership checks |
| `InvitationService` | Token generation (high-entropy random); token-to-invitation resolution |
| `RsvpService` | RSVP submission and changes; capacity enforcement; waitlist management; seat promotion — all within a single locked transaction |
| `DashboardService` | Aggregates confirmed / waitlisted / no / maybe / total-invited counts from committed RSVP state |
| `EventRepository` | Event rows; the row locked during seat allocation |
| `InvitationRepository` | Invitation rows; token index |
| `RsvpRepository` | RSVP rows; confirmed-count queries; waitlist position queries |

Controllers contain no business logic. `RsvpService` is the most critical component — every capacity invariant is enforced there.

---

### Data Model

**Event:** `id`, `hostId`, `title`, `description`, `startTime`, `location`, `maxCapacity` (nullable), `status` (OPEN / CLOSED / CANCELED), `createdAt`, `updatedAt`

**Invitation:** `id`, `eventId`, `email`, `inviteToken` (unique, high-entropy), `createdAt`

**RSVP:** `id`, `invitationId`, `status` (PENDING / YES_CONFIRMED / YES_WAITLISTED / NO / MAYBE), `respondedAt`, `version`

`waitlistPosition` is not stored. Waitlist ordering is derived entirely from `respondedAt` ascending. No field needs to be maintained or resequenced when a promotion occurs.

Required database constraints: `UNIQUE(inviteToken)`, `UNIQUE(eventId, email)`, index on `inviteToken`, index on `rsvps.invitationId`, index on `rsvps.status` filtered by `eventId`.

---

### State Machines

**Event states:**

```
create ──► OPEN ──► CLOSED      (host closes)
                └──► CANCELED   (host cancels)
```

`LOCKED` is not a persisted status. It is derived: when `currentTime >= startTime`, RSVP writes are rejected. No scheduler or background job is needed.

**RSVP states:**

```
PENDING ──► YES_CONFIRMED  (seat available)
        ──► YES_WAITLISTED  (capacity full)
        ──► NO
        ──► MAYBE

YES_CONFIRMED ──► NO      (frees a seat; triggers promotion)
              ──► MAYBE   (frees a seat; triggers promotion)

YES_WAITLISTED ──► YES_CONFIRMED  (auto-promotion only; not invitee-initiated)

NO / MAYBE ──► any valid state  (before lock)

Any state ──► rejected  (if event is LOCKED, CLOSED, or CANCELED)
```

MAYBE does not consume a capacity slot. The invitee's intent (Yes / No / Maybe) is accepted as input; the outcome status is always derived by the backend based on current state.

---

### Invariants

These must always hold. They take precedence over implementation convenience.

| ID | Invariant |
|---|---|
| I1 | One RSVP record per invitation at all times |
| I2 | Only the host may perform host actions on their own event |
| I3 | An invitee's token grants access only to their own invitation and RSVP |
| I4 | No RSVP change accepted when `currentTime >= startTime` |
| I5 | No RSVP change accepted when event is CLOSED or CANCELED |
| I6 | Confirmed attendee count never exceeds `maxCapacity` |
| I7 | When capacity is full, a new Yes cannot become CONFIRMED directly |
| I8 | Seat promotion selects the next waitlisted record by FIFO timestamp — no other ordering |
| I9 | MAYBE does not count toward capacity |
| I10 | Once CANCELED, invitee responses are read-only |

---

### Concurrency

The critical scenario is two simultaneous Yes RSVPs for the last available seat. Without protection, both read the same confirmed count, both decide CONFIRMED, and I6 is violated.

**Approach:** Every RSVP write acquires a pessimistic lock on the event row. Inside that locked transaction: read event status and time, count confirmed records, derive the outcome, write the RSVP record, and — if a seat was freed — promote the next waitlisted record in FIFO order. All in one transaction. The lock is released on commit.

This serializes all RSVP writes per event. Promotion is synchronous and inside the same transaction — never async, never triggered after the transaction commits.

The event status check and the time check must both happen after the lock is held, not before. If either check is done pre-lock, a concurrent close or cancel can commit between the check and the write, allowing an RSVP into a nominally closed event.

---

### Trust Boundaries and Security

Two entry points into the system:

- **Host identity** — established by the `X-Host-Id` HTTP request header. The backend reads `hostId` exclusively from this header. For v1, the scaffold sets this header. No general auth system is implemented. Requests to host-privileged endpoints without this header are rejected with 401.
- **Invite token** — the only invitee credential; must be high-entropy and cryptographically random; must not be derived from internal IDs.

Authorization rules:
- Host endpoints verify `hostId` on the event record matches the caller on every request.
- Invitee endpoints resolve all access through the token; `invitationId` or `eventId` supplied by the client do not grant access on their own.
- The full attendee list is host-only. Invitee-facing endpoints must not return it.
- All input is validated server-side. Client-side validation is convenience only.
- Invite tokens appear in URL paths. Log configuration must not record full request URLs before any non-local deployment.

---

### Key Design Decisions and Rationale

| Decision | Choice | Why |
|---|---|---|
| Single source of truth | Backend | Frontend must not derive seat counts or RSVP outcomes |
| Lock-after-start | Derived from clock, not persisted | Eliminates scheduler failure modes; always consistent with real time |
| RSVP status model | Combined enum (PENDING / YES_CONFIRMED / YES_WAITLISTED / NO / MAYBE) | Simpler for v1; no risk of two fields disagreeing |
| Seat allocation | Pessimistic event-row lock inside one transaction | Safest correctness guarantee under contention; complexity is manageable at v1 scale |
| Waitlist ordering | FIFO by `respondedAt`, no stored position | Deterministic, auditable; no resequencing needed on promotion |
| Dashboard freshness | Polling / re-fetch | Sufficient for v1; avoids WebSocket infrastructure |
| Invitee access | Token-only, no accounts | Matches stated requirement for frictionless RSVP |
| Promotion timing | Synchronous, inside the seat-freeing transaction | Async promotion risks double-promotion and violates I6 |
| Host auth mechanism | `X-Host-Id` request header | Consistent with v1 scaffold; backend-controlled value; simple to test |
| Duplicate invites | Rejected at DB layer via `UNIQUE(eventId, email)` | Prevents ghost records; constraint is enforced even under concurrent invite calls |
| Client intent vs. status | Client sends `intent` (YES/NO/MAYBE); backend derives outcome status | Ensures capacity rules can never be bypassed from the client |
| Event editing | Out of scope for v1 | Adds workflow complexity without v1 requirement; can be added cleanly later |
| OPEN + LOCKED | `LOCKED` derived at read time; `OPEN` persisted | No background job; no clock-driven state transition; always consistent |
| CLOSED state | Terminal in v1; no reopen | Keeps state machine simple; backward transitions add concurrency surface |
| Invite token expiry | No expiry in v1 | Single time constraint (`currentTime >= startTime`) is sufficient; expiry adds a time dimension to every token lookup |
| Waitlist reordering | No — FIFO only | Deterministic, requires no host action; manual reordering is a future workflow |
| Dashboard after cancellation | Always readable by host | Read access is never a correctness risk; cancellation only blocks RSVP writes |

---

### Risks That Shape the Design

The following risks are unmitigated without deliberate action:

- **R-C1** — Unprotected concurrent writes can exceed capacity. Mitigation: the locked transaction sequence in §12.
- **R-C2** — Async promotion can double-promote. Mitigation: promotion is synchronous and inside the locked transaction.
- **R-C3** — Pre-lock status check admits RSVPs into a closed event. Mitigation: check after the lock is held.
- **R-C4** — Client-supplied RSVP outcome bypasses capacity logic. Mitigation: outcome is always derived server-side.
- **R-D2** — JPA cache returns stale entity before the locked query. Mitigation: locked load must bypass the first-level cache.
- **R-D3** — `ddl-auto: update` does not add constraints to existing tables. Mitigation: all constraints applied on initial `CREATE TABLE`.
- **R-O1** — Tokens logged in URL access logs. Mitigation: log configuration must redact token paths before non-local deployment.

---

### Open Questions

All scoped open questions have been resolved for v1. Full decisions are in §06 and the Key Design Decisions table above. Future scope questions that would require new workflows or model changes if the product evolves are recorded as assumption risks in §14 (R-A1–R-A5).

---

### What Is Not in This Design

These are non-goals and out-of-scope items. They are listed explicitly because they are adjacent to the feature and could be mistakenly assumed:

- No email delivery. Invite tokens are generated and stored; how they reach the invitee is outside this design.
- No real-time push. The dashboard uses polling; WebSocket infrastructure is not included.
- No RSVP history or audit log. The design tracks only the current RSVP state.
- No event editing. Event fields are set on creation and not changed.
- No general user authentication. Host identity relies on the scaffold; invitees have no accounts.
- No organization-level tenancy, admin roles, or multi-host management.


