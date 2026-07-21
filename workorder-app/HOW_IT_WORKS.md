# How This App Works

Three independent pieces, talking over HTTP:

```
Browser  <--HTTP-->  Frontend (Node, port 3000)  <--HTTP-->  Backend (Java, port 8080)  <--would connect to-->  SQL Server
```

- The **frontend** is just a webpage. It has no database access and no
  business logic — it only knows how to call the backend's API and
  display what comes back.
- The **backend** is the REST API. All the actual logic lives here:
  creating work orders, validating input, enforcing the one business
  rule (can't reopen a completed order), and talking to storage.
- The **database** (SQL Server) is where data would live permanently in
  a real deployment. Right now the backend uses an in-memory stand-in
  instead (more on why below), but the real schema and the real
  database code both exist and are ready to be wired in.

---

## The database (`sql/schema.sql`)

One table: `wo.WorkOrders`.

| Column | Type | Notes |
|---|---|---|
| `Id` | `INT IDENTITY` | Auto-incrementing primary key |
| `Title` | `NVARCHAR(200)` | Required, can't be blank |
| `Description` | `NVARCHAR(MAX)` | Optional |
| `Status` | `VARCHAR(20)` | One of 5 fixed values, defaults to `OPEN` |
| `AssignedTo` | `NVARCHAR(100)` | Optional, just a name — no Users table |
| `CreatedAt` / `UpdatedAt` | `DATETIME2` | Both default to "now" |

A few details worth knowing if asked:

- **`CHECK` constraint on `Status`** — the database itself refuses any
  status value outside `OPEN, IN_PROGRESS, ON_HOLD, COMPLETED,
  CANCELLED`. That's a second line of defense in addition to the
  backend's own validation.
- **A trigger keeps `UpdatedAt` honest.** Any `UPDATE` to a row
  automatically stamps `UpdatedAt = SYSUTCDATETIME()`, so even if some
  other tool or script updates a row directly, the timestamp still
  moves. Belt and suspenders.
- **An index on `Status`** — because filtering/sorting a worklist by
  status ("show me everything that's still open") is the most common
  query a system like this gets, so it's worth indexing for.
- **No foreign keys to a Users table.** `AssignedTo` is just a plain
  text field. That's a deliberate simplification, not an oversight —
  there's no login system in this project, so there's nothing to link
  it to yet.

This script isn't currently connected to anything running — see "Why
it's not using real SQL Server yet" below.

---

## The backend (Java, Spring Boot)

Lives in `backend/src/main/java/com/example/workorder/`. Structure:

```
WorkOrderApplication.java     starts the app
WorkOrderController.java      all the routes
GlobalExceptionHandler.java   turns exceptions into HTTP responses
model/
  WorkOrder.java              the data itself
  Status.java                 the 5 allowed statuses + the "terminal" rule
repo/
  WorkOrderRepository.java    the storage contract (an interface)
  InMemoryWorkOrderRepository.java   storage in a HashMap (used by default)
  JdbcWorkOrderRepository.java       storage in real SQL Server (ready, off by default)
validation/
  ValidationException.java   bad input -> HTTP 400
  NotFoundException.java     unknown id -> HTTP 404
  ConflictException.java     breaks a business rule -> HTTP 409
```

This is a real Maven project (`pom.xml`) using Spring Boot — an
embedded web server, Jackson for JSON, dependency injection wiring the
pieces together. Run it with `mvn spring-boot:run`, test it with
`mvn test`.

### How a request actually flows

Say the frontend sends `POST /api/workorders` with a title in the body.
Here's the path that request takes:

1. **`WorkOrderApplication.main()`** starts Spring Boot, which spins up
   an embedded web server and scans for classes annotated
   `@RestController`, `@Repository`, etc. — it finds
   `WorkOrderController` and registers its routes automatically.
2. **`WorkOrderController.create()`** is the method annotated
   `@PostMapping` — Spring routes the request straight to it, and
   Jackson has already turned the JSON body into a `Map<String,
   Object>` before the method even runs (that's the `@RequestBody`
   annotation doing its job).
3. The method checks the fields: is `title` present and non-blank? Is
   it under 200 characters? Are there any fields in the request that
   shouldn't be there (like a typo)? Any failure throws a
   `ValidationException`.
4. If everything checks out, it calls
   `repository.create(title, description, assignedTo)`.
5. The repository (by default, `InMemoryWorkOrderRepository`) builds a
   new `WorkOrder` object with a fresh id, status `OPEN`, and the
   current timestamp, stores it, and returns it.
6. The controller method returns that `WorkOrder` object directly —
   Spring's Jackson integration turns it into JSON automatically and
   sends it back with status code `201 Created`.

If step 3 throws a `ValidationException`, the request never reaches
step 4 — Spring notices the exception, looks for a matching
`@ExceptionHandler` in `GlobalExceptionHandler`, and turns it into a
`400` response with an error message instead of letting anything else
run.

That "exception type maps to HTTP status code" pattern is used for all
error handling in this API:

| Exception | HTTP status | When it happens |
|---|---|---|
| `ValidationException` | 400 | Bad input — blank title, wrong type, unknown field |
| `NotFoundException` | 404 | No work order with that id |
| `ConflictException` | 409 | Trying to reopen a `COMPLETED`/`CANCELLED` order |
| anything else | 500 | Unexpected bug — logged server-side, generic message to client |

### The routes

| Method | Path | Does |
|---|---|---|
| `POST` | `/api/workorders` | Create a new work order |
| `GET` | `/api/workorders` | List all of them (supports `?status=OPEN` filter) |
| `GET` | `/api/workorders/{id}` | Fetch one by id |
| `PATCH` | `/api/workorders/{id}` | Update title/description/assignedTo/status — you only need to send the fields you're changing |

### The one business rule

Status can be `OPEN`, `IN_PROGRESS`, `ON_HOLD`, `COMPLETED`, or
`CANCELLED`. The last two are "terminal" — once a work order is
completed or cancelled, the API won't let you move it to any other
status. If you try, you get a `409 Conflict`, not a `400` — the
request itself is well-formed (a real status value), it's just not
allowed given the order's current state. That distinction (400 vs 409)
is a good thing to be able to explain: 400 means "your request is
broken," 409 means "your request is fine, but it conflicts with the
current state of the thing you're changing."

### Why request bodies are a `Map`, not a typed class

You'd usually see JSON bound straight to a class (`CreateWorkOrderRequest`
with a `title` field, etc.). That works great for `POST`, but `PATCH`
here is a partial update — the client only sends the fields it's
changing. A plain class can't tell "the client didn't send this field"
apart from "the client sent this field as `null`" (both would just show
up as `null` on the object). Reading the JSON as a raw
`Map<String, Object>` and calling `.containsKey()` tells the difference
cleanly, so that's what both `create()` and `update()` do. Jackson still
does the actual JSON parsing — it's just parsing into a `Map` instead of
a custom class.

### Why there's an in-memory store by default instead of real SQL Server

`InMemoryWorkOrderRepository` is the default so you can run the whole
app with zero setup — no database needed to try it out. Data lives in
a `HashMap` and disappears when the app restarts.

`JdbcWorkOrderRepository.java` is a complete, real implementation that
talks to SQL Server using the schema in `sql/schema.sql`. Both classes
implement the same `WorkOrderRepository` interface, and Spring decides
which one to create based on the `workorder.storage` property in
`application.properties` — set it to `jdbc` (and fill in the
`spring.datasource.*` lines) to switch to a real database with no code
changes.

### The tests

`backend/src/test/java/com/example/workorder/WorkOrderApiTests.java` —
run with `mvn test`. Real JUnit 5 tests using Spring Boot's test
support: each test method boots the actual app on a random free port
and hits it with real HTTP calls via `TestRestTemplate`, checking the
responses — not mocks. Seven tests:

1. A valid create returns `201` with the right defaults.
2. A blank title gets rejected, and confirms nothing got saved.
3. The list endpoint reflects what was created, and `?status=`
   filtering actually filters.
4. Looking up an id that doesn't exist returns `404`.
5. Changing status actually updates the row, and `updatedAt` moves.
6. A bogus status string gets rejected.
7. Trying to reopen a completed order returns `409` — this is the one
   pinning down the actual business rule, so it's the most important
   test in the suite.

---

## The frontend (Node.js + TypeScript)

Lives in `frontend/`. Two completely separate scripts:

- **`src/server.ts`** — runs on the server (Node), compiles to
  `dist/server.js`. Its only job is serving static files
  (`index.html`, `app.js`, `styles.css`) to the browser. It also does
  one small extra thing: it injects the backend's URL into the HTML
  page before sending it, so the browser knows where to send its API
  calls. That URL comes from the `API_BASE_URL` environment variable
  (defaulting to `http://localhost:8080`).

- **`src/app.ts`** — runs in the browser, compiles to `public/app.js`.
  This is the actual UI logic: loading the list, rendering the table,
  handling the "Add work order" form, and handling the status
  dropdown.

These are compiled separately (two different `tsconfig` files) because
they run in two completely different environments — one has access to
the file system and environment variables (Node), the other runs
inside the browser and has access to the DOM (`document`, `fetch`,
etc.) but nothing server-side.

### What happens when you load the page

1. Browser requests `/` from the frontend server.
2. `server.ts` reads `index.html`, injects a small script tag setting
   `window.__API_BASE_URL__`, and sends it back.
3. The browser also requests `/app.js` and `/styles.css`, which are
   served as-is.
4. Once `app.js` runs, it calls `loadWorkOrders()`, which does
   `fetch('http://localhost:8080/api/workorders')` — a request
   straight to the Java backend, not through the Node server at all.
5. The backend responds with JSON; `app.js` builds table rows from it
   and drops them into the page.

### Why the backend needs CORS enabled

Step 4 above is a request from a page served on port 3000 to an API on
port 8080 — different ports count as a different "origin" as far as
the browser's security rules are concerned. Without explicit
permission, the browser blocks that request. That's why
`WorkOrderController` has `@CrossOrigin(origins = "*")` on it — that
tells Spring to add the `Access-Control-Allow-Origin` header to every
response, which is what tells the browser "yes, it's fine for a page
from a different origin to call this API."

### Adding a work order / changing status

Both go through the same pattern: the button/dropdown handler calls
`apiRequest()` (a small wrapper around `fetch`), which sends a
`POST` or `PATCH` request with a JSON body, waits for the response, and
either re-renders the list (on success) or shows the error message from
the backend's `{"error": "..."}` response (on failure). Nothing fancy —
no state management library, just plain DOM updates.

### Why there's no framework (no React, no Express)

At this size — one list, one form, one dropdown — a framework's setup
cost isn't worth paying yet. `server.ts` uses Node's built-in `http`
module instead of Express because it's only serving static files. If
this grows to multiple screens or more complex client-side state,
React would earn its place.

---

## Talking points if someone asks "why did you build it this way"

- **"Why Spring Boot and not something lighter?"** — At this size a
  plain JDK server would technically work, but Spring Boot gets you
  request routing, JSON handling, and dependency injection for free,
  and it's what most teams already standardize on for a Java REST API.
- **"Why is data in memory instead of SQL Server?"** — So the app runs
  with zero setup — no database needed just to try it. The real schema
  and a full JDBC implementation both exist (`JdbcWorkOrderRepository`)
  and are one config change away from being live.
- **"What's the one business rule in here?"** — Completed or cancelled
  work orders can't be reopened. Trying returns a 409, and it's the
  most heavily tested behavior in the suite.
- **"How do the three pieces talk to each other?"** — Frontend calls
  backend directly over HTTP/JSON from the browser (not through the
  Node server); backend would talk to SQL Server over JDBC once
  `workorder.storage=jdbc` is set.
- **"What would you change first with more time?"** — Real SQL Server
  behind the existing JDBC code, basic auth instead of a free-text
  `assignedTo` field, and pagination on the list endpoint.
