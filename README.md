# workorder-app
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
