# Work Order Manager

Schema, API, and a UI for tracking work orders.

```
sql/schema.sql   SQL Server table
backend/         Java REST API (Spring Boot, Maven)
frontend/        Node/TS front end
```

## Run it

Needs a JDK 17+ and Maven, both with internet access (Maven pulls
Spring Boot, Jackson, etc. from Maven Central on first run).

```bash
cd backend
mvn spring-boot:run     # http://localhost:8080
mvn test                 # runs the test suite
```

```bash
cd frontend
npm install && npm start   # http://localhost:3000
```

Don't have Maven installed? Grab it from
https://maven.apache.org/download.cgi — same idea as installing a JDK,
just add it to your PATH. Run `mvn -version` to confirm it worked.

## Assumptions

- No login/auth — `assignedTo` is just a name, not a real user account.
- One status set: `OPEN, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED`.
- Once a work order is `COMPLETED` or `CANCELLED`, it can't be reopened
  through the API. That's the one real business rule here, and it's the
  thing the tests focus on.
- Update is one `PATCH` endpoint covering title, description, assignedTo,
  and status — not four separate routes.
- No pagination.

## Tradeoffs

- **Storage is in-memory by default**, not a live SQL Server. Set
  `workorder.storage=jdbc` in `application.properties` (plus the
  `spring.datasource.*` lines below it) to point at a real SQL Server
  instance running `sql/schema.sql` — `JdbcWorkOrderRepository.java` is
  a full, ready implementation, just off by default so you don't need a
  database just to try the app.
- **Request bodies come in as a raw `Map<String, Object>`**, not a typed
  DTO class. `PATCH` is a partial update, and a plain DTO can't tell
  "field not sent" apart from "field sent as null" — reading the map
  and checking `containsKey()` handles that cleanly.
- **Frontend is vanilla TS**, no React, no bundler. Fine at this size —
  one list, one form. Would switch to React the moment there's more
  than a couple of screens.

## With more time

- Point it at a real SQL Server and test that path with Testcontainers.
- Auth, and a real Users table instead of a free-text `assignedTo`.
- Pagination on the list endpoint.
- Optimistic concurrency on updates (two people editing the same work
  order shouldn't silently clobber each other).
  - Optimistic concurrency on updates (two people editing the same work
  order shouldn't silently clobber each other).

## Deploying it

Package it as a jar (`mvn package`) and run it in a small JRE container
behind a load balancer, 2+ replicas. DB connection string and port come
from environment/properties — already wired up that way
(`WORKORDER_PORT`, `spring.datasource.*`). Database goes on a managed
SQL Server instance (Azure SQL / RDS), not something self-hosted, with
backups on from day one — run `schema.sql` through a migration tool
rather than by hand. Frontend is just static files, so it can sit behind
a CDN with no server process running at all in production.

## Monitoring it

Spring Boot Actuator gets you a `/actuator/health` endpoint and basic
metrics almost for free — worth adding before this goes anywhere near
production. Track request rate, latency, and error rate by status code
— a spike in 409s means people are hitting the terminal-status rule, a
spike in 500s means something's actually broken, and those need
different responses. Page on sustained 5xx or failed health checks;
just ticket on elevated 4xx, since that's usually a client bug and not
an incident.

## Tests

Seven tests, in `backend/src/test/.../WorkOrderApiTests.java`, run via
`mvn test`:

1. Valid create → 201, right defaults.
2. Blank title → 400, and nothing gets saved.
3. List reflects what was created, and `?status=` filtering works.
4. Unknown id → 404.
5. Valid status change → 200, `updatedAt` actually moves.
6. Garbage status value → 400.
7. Trying to reopen a `COMPLETED` order → 409.

That last one is the actual business rule in this API, so it's the one
I cared most about pinning down. They boot the real app on a random
port and hit it with real HTTP calls — not mocks.

## AI use

Claude was very helpfuyl in getting this windows machiine set up to run locally.
I had no JDK or Maven so everything was installed and configured from scratch. The troubleshooting went very quickly and I was able to get up and going in little time. I had Claude review my project, look for improvements, double check me that test were comprehensive, and ensure that the documentation (readme and how it works) were comprehensive to ensure any future developer who worked on this project would be able to pick it up quickly. 
