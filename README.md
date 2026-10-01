# Log Monitoring Engine

An asynchronous log processor built on Java 17, Spring Boot 3, RabbitMQ and PostgreSQL, with a React and TypeScript monitoring dashboard. The REST endpoint only publishes to RabbitMQ and never writes to the database itself, so ingestion keeps up during traffic spikes. Background consumers handle alerting and storage.

```
                                   ┌──────────── log.topic.exchange (topic) ────────────┐
POST /api/v1/logs/submit ──publish─┤                                                     │
   routing key = <service>.<sev>   │  *.critical ──► critical.alerts.queue ──► alert telemetry (console), then PostgreSQL
                                   │  *.info     ──► info.storage.queue    ──► PostgreSQL (system_logs)
                                   └─────────────────────────────────────────────────────┘
                     failed after retries ──► log.dead-letter.exchange ──► log.dead-letter.queue
```

| Severity   | Routing key             | Destination                               |
|------------|-------------------------|-------------------------------------------|
| `CRITICAL` | `<service>.critical`    | `critical.alerts.queue` → immediate alert, then persisted |
| `WARNING`  | `<service>.info`        | `info.storage.queue` → persisted          |
| `INFO`     | `<service>.info`        | `info.storage.queue` → persisted          |

## Prerequisites

- JDK 17+
- Maven 3.9+
- Docker with Compose v2
- Node.js 20.19+ or 22.12+ (dashboard only)

## Run

```bash
# 1. Start RabbitMQ + PostgreSQL (schema is created automatically)
docker compose up -d --wait

# 2. Build, test and boot the engine
mvn clean spring-boot:run
```

```bash
# 3. In a second terminal, start the dashboard
cd frontend
npm install
npm run dev
```

- Dashboard: `http://localhost:5173` (create an account on first visit)
- API: `http://localhost:8080`
- RabbitMQ dashboard: `http://localhost:15672` (guest / guest)
- Health: `GET /actuator/health` (public)

## Authentication

| Caller | Endpoint | Credential |
|---|---|---|
| Log producers | `POST /api/v1/logs/submit` | `X-API-Key` header (local default `local-dev-ingest-key`) |
| People | Dashboard and read API | Account sign-in, then `Authorization: Bearer <JWT>` |

**Accounts:**
- **Sign-up is open:** anyone who can reach the dashboard can create an account, and every account can read all logs.
- **Passwords:** 8–72 characters, stored as BCrypt hashes. Emails are matched case-insensitively.
- **Sessions:** a sign-in returns a JWT valid for 12 hours (`JWT_EXPIRATION`). The dashboard keeps it in `localStorage` and goes back to the sign-in page when it expires.
- **Rate limit:** sign-in and sign-up allow 10 attempts per IP per minute (`AUTH_ATTEMPTS_PER_MINUTE`). Further attempts get `429` with a `Retry-After` header.
- **Login check:** a sign-in takes the same time whether or not the email exists, so it doesn't reveal which emails have accounts.

The two credentials are separate:
- The ingest key can only submit logs. A producer that leaks it can't read anything.
- A dashboard account can't submit logs.

The local defaults only apply outside the `prod` profile. The Docker image runs with `prod`, and there the engine refuses to start unless `INGEST_API_KEY` (12+ characters) and `JWT_SECRET` (32+ characters) are set.

| Endpoint | Body | Returns |
|---|---|---|
| `POST /api/v1/auth/signup` | `{"email","password"}` | `201` with `{token, tokenType, expiresInSeconds, user}`; `409` if the email is taken |
| `POST /api/v1/auth/login` | `{"email","password"}` | `200` with the same shape; `401` on a wrong email or password |
| `GET /api/v1/auth/me` | — | The signed-in user (needs the bearer token) |

### Port conflicts

If another service already uses one of these ports, override the port:

```bash
POSTGRES_HOST_PORT=55432 docker compose up -d --wait
SERVER_PORT=8090 DB_URL=jdbc:postgresql://localhost:55432/log_monitoring_db mvn clean spring-boot:run
cd frontend && VITE_API_PROXY_TARGET=http://localhost:8090 npm run dev
```

## Dashboard

The dashboard is a single-page React 19 + TypeScript + Tailwind CSS v4 console, built with Vite. It lives in `frontend/`.

- **Summary cards:** Total Logs, Critical Failures (red) and Standard Info Traces (blue). The counts come from `GET /api/v1/logs/summary` and cover the whole database, not just the rows currently loaded.
- **Log Streaming Ledger:** a scrollable table of the latest 500 logs, newest first, with a sticky header. Click a row to expand a long message or stack trace.
- **Filters:** All Logs, Critical Only and Info Only filter the loaded rows instantly in the browser. Info Only includes WARNING, matching the `*.info` storage route.
- **Loading:** an animated skeleton shows during the first load and manual refreshes. Auto-refresh polls every 10 seconds in place without the skeleton, and pauses while the browser tab is hidden.
- **Errors:** a failed request shows a banner with a Retry button.

In development, Vite proxies `/api` to the backend. In production, the Docker image bundles the dashboard into Spring Boot, so it's served from the same origin as the API. Either way, no CORS setup is needed.

## Read API

| Endpoint | Returns |
|---|---|
| `GET /api/v1/logs?limit=500` | The latest logs, newest first. `limit` is 1–2000 (default 500). |
| `GET /api/v1/logs/summary` | `{"total":7,"critical":2,"standard":5}` |

Both endpoints need a signed-in account's token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"your-password"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/logs/summary
```

## Try it

```bash
# Critical: triggers the alert pipeline (watch the application console)
curl -i -X POST http://localhost:8080/api/v1/logs/submit \
  -H 'Content-Type: application/json' -H 'X-API-Key: local-dev-ingest-key' \
  -d '{"serviceName":"payment-service","severity":"CRITICAL","logMessage":"Payment gateway timeout after 30s"}'

# Info: persisted to PostgreSQL in the background
curl -i -X POST http://localhost:8080/api/v1/logs/submit \
  -H 'Content-Type: application/json' -H 'X-API-Key: local-dev-ingest-key' \
  -d '{"serviceName":"auth-service","severity":"INFO","logMessage":"User login succeeded"}'

# Invalid payload: 400 with field-level errors
curl -i -X POST http://localhost:8080/api/v1/logs/submit \
  -H 'Content-Type: application/json' -H 'X-API-Key: local-dev-ingest-key' \
  -d '{"serviceName":"bad.name","severity":"DEBUG","logMessage":""}'

# Missing or wrong API key: 401
curl -i -X POST http://localhost:8080/api/v1/logs/submit \
  -H 'Content-Type: application/json' \
  -d '{"serviceName":"auth-service","severity":"INFO","logMessage":"no key"}'
```

Successful submissions return `202 Accepted`:

```json
{"eventId":"9b83ae6e-...","routingKey":"auth-service.info","status":"QUEUED","acceptedAt":"2026-10-01T16:11:21.436499"}
```

Check the stored rows:

```bash
docker exec -it log-engine-postgres psql -U postgres -d log_monitoring_db \
  -c 'SELECT * FROM system_logs ORDER BY id DESC LIMIT 10;'
```

## Request contract

| Field         | Rules                                                                    |
|---------------|--------------------------------------------------------------------------|
| `serviceName` | 1–64 chars, letters/digits/`-`/`_`, no dots (it becomes a routing-key word); stored lowercase |
| `severity`    | `CRITICAL`, `WARNING` or `INFO` (case-insensitive)                       |
| `logMessage`  | Required, up to 65,536 chars (stack traces welcome)                      |

## Reliability and performance

- **Durable topology:** the exchange and queues are durable, so queued logs survive a broker restart.
- **Publisher confirms and returns:** the broker acknowledges each publish. A NACK or an unroutable message is logged at `ERROR` with its event ID.
- **Broker outages:** if the broker is unreachable, the API returns `503` so the client can retry.
- **Consumer retries:** a failed message is retried 3 times with exponential backoff. After that it goes to `log.dead-letter.queue` and is never requeued in a loop.
- **Throttling:**
  - The storage consumer prefetches 50 messages, runs 2–4 threads and uses a Hikari pool of 10 connections. This caps database write pressure no matter how fast logs arrive.
  - Critical alerts use prefetch 1 and 2–8 threads, so they are never stuck behind a backlog.
  - Tune all of this under `log-engine.consumers` in `application.yml`.
- **Safe deserialization:** payloads are always converted to the listener's declared type. The `__TypeId__` header is never trusted.
- **Event time:** timestamps are taken at ingestion, in UTC. Stored rows show when a log arrived, not when the consumer drained it.

## Configuration

Every value can be overridden with an environment variable:

| Variable | Local default | In `prod` |
|---|---|---|
| `PORT` / `SERVER_PORT` | `8080` | `PORT` is injected by the host |
| `DB_URL` | `jdbc:postgresql://localhost:5432/log_monitoring_db` | required |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `password` | required |
| `DB_POOL_SIZE` | `10` | `5` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | replaced by `RABBITMQ_URL` |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` / `RABBITMQ_VHOST` | `guest` / `guest` / `/` | replaced by `RABBITMQ_URL` |
| `RABBITMQ_URL` | — | required, e.g. `amqps://user:pass@host/vhost` |
| `INGEST_API_KEY` | `local-dev-ingest-key` | required, 12+ chars |
| `JWT_SECRET` | a fixed dev value | required, 32+ chars |
| `JWT_EXPIRATION` | `PT12H` | `PT12H` |
| `AUTH_ATTEMPTS_PER_MINUTE` | `10` | `10` |
| `CRITICAL_MAX_CONSUMERS` / `STORAGE_MAX_CONSUMERS` | `8` / `4` | `4` / `2` |

RabbitMQ's `guest` user only accepts connections from localhost.

## Deploy to Render

The engine runs as one free Render web service. PostgreSQL and RabbitMQ come from free external providers.

1. **PostgreSQL:** create a free database on [Neon](https://neon.tech). Neon gives you a connection string like `postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require`. Split it into three values:
   - `DB_URL` = `jdbc:postgresql://HOST/DBNAME?sslmode=require`
   - `DB_USERNAME` = `USER`
   - `DB_PASSWORD` = `PASSWORD`

   The engine creates its table on first start.
2. **RabbitMQ:** create a free "Little Lemur" instance on [CloudAMQP](https://www.cloudamqp.com) and copy its AMQP URL (`amqps://...`) into `RABBITMQ_URL`. The engine declares its exchange and queues on first start.
3. **Render:** push this repo to GitHub. In Render, choose **New → Blueprint** and select the repo. `render.yaml` creates the service:
   - It asks for the four connection values above.
   - It generates `INGEST_API_KEY` and `JWT_SECRET` for you. Find them under the service's **Environment** tab.
4. **Open it:** go to `https://<service>.onrender.com` and create an account.
5. **Connect producers:** give each producer the service URL plus `/api/v1/logs/submit`, and the `INGEST_API_KEY`.

Free Render services sleep after 15 minutes without traffic, and the first request after that takes about a minute to wake the service. Producers with short timeouts (the AI Expense Ledger uses 2 seconds) drop the events sent during that wake-up.

## Project layout

```
src/main/java/com/logmonitoring/engine/
├── LogMonitoringEngineApplication.java
├── config/RabbitMQConfig.java          # exchange, queues, bindings, DLQ, converters, listener factories
├── controller/LogIngressController.java  # POST /submit
├── controller/LogQueryController.java    # GET ledger + summary
├── controller/AuthController.java        # sign-up, sign-in, me
├── controller/GlobalExceptionHandler.java
├── consumer/LogConsumers.java          # critical alert + persistence listeners
├── dto/                                # request, response and message records
├── model/SystemLog.java                # JPA entity
├── model/Severity.java                 # severity → routing key mapping
├── repository/LogRepository.java
└── security/                           # API-key + JWT filters, sign-in rate limit, secrets
src/main/resources/
├── application.yml
├── application-prod.yml                # used by the Docker image; secrets required
└── schema.sql                          # also mounted into Postgres' init directory
Dockerfile                              # builds the dashboard into the Spring Boot jar
render.yaml                             # Render Blueprint
frontend/
├── vite.config.ts                      # /api dev proxy (VITE_API_PROXY_TARGET)
└── src/
    ├── App.tsx                         # state, fetching, filtering, auto-refresh
    ├── api/logClient.ts                # fetch client for the read API
    ├── types.ts
    └── components/                     # SummaryCards, FilterBar, LogLedger, SeverityBadge
```

## Shut down

```bash
docker compose down        # keep data
docker compose down -v     # also delete volumes
```
