# FinAlly — AI Trading Workstation

## Project Specification

## 1. Vision

FinAlly (Finance Ally) is a visually stunning AI-powered trading workstation that streams live market data, lets users trade a simulated portfolio, and integrates an LLM chat assistant that can analyze positions and execute trades on the user's behalf. It looks and feels like a modern Bloomberg terminal with an AI copilot.

This is the capstone project for an agentic AI coding course. It is built entirely by Coding Agents demonstrating how orchestrated AI agents can produce a production-quality full-stack application. Agents interact through files in `planning/`.

---

## 2. User Experience

### First Launch

The user runs a single Docker command (or a provided start script). A browser opens to `http://localhost:8000`. No login, no signup. They immediately see:

- A watchlist of 10 default tickers with live-updating prices in a grid
- $10,000 in virtual cash
- A dark, data-rich trading terminal aesthetic
- An AI chat panel ready to assist

### What the User Can Do

- **Watch prices stream** — prices flash green (uptick) or red (downtick) with subtle CSS animations that fade
- **View sparkline mini-charts** — price action beside each ticker in the watchlist, accumulated on the frontend from the SSE stream since page load (sparklines fill in progressively)
- **Click a ticker** to see a larger detailed chart in the main chart area
- **Buy and sell shares** — market orders only, instant fill at current price, no fees, no confirmation dialog
- **Monitor their portfolio** — a heatmap (treemap) showing positions sized by weight and colored by P&L, plus a P&L chart tracking total portfolio value over time
- **View a positions table** — ticker, quantity, average cost, current price, unrealized P&L, % change
- **Chat with the AI assistant** — ask about their portfolio, get analysis, and have the AI execute trades and manage the watchlist through natural language
- **Manage the watchlist** — add/remove tickers manually or via the AI chat

### Visual Design

- **Dark theme**: backgrounds around `#0d1117` or `#1a1a2e`, muted gray borders, no pure black
- **Price flash animations**: brief green/red background highlight on price change, fading over ~500ms via CSS transitions
- **Connection status indicator**: a small colored dot (green = connected, yellow = reconnecting, red = disconnected) visible in the header
- **Professional, data-dense layout**: inspired by Bloomberg/trading terminals — every pixel earns its place
- **Responsive but desktop-first**: optimized for wide screens, functional on tablet

### Color Scheme

- Accent Yellow: `#ecad0a`
- Blue Primary: `#209dd7`
- Purple Secondary: `#753991` (submit buttons)

---

## 3. Architecture Overview

### Single Container, Single Port

```
┌─────────────────────────────────────────────────┐
│  Docker Container (port 8000)                   │
│                                                 │
│  Spring Boot (Java 25, WebFlux / Netty)         │
│  ├── /api/*          REST endpoints             │
│  ├── /api/stream/*   SSE streaming (Flux)       │
│  └── /*              Static file serving        │
│                      (Next.js export)           │
│                                                 │
│  SQLite database (volume-mounted)               │
│  Scheduled tasks: market data sim/poller,       │
│                   portfolio snapshots           │
└─────────────────────────────────────────────────┘
```

- **Frontend**: Next.js + TypeScript, static export (`output: 'export'`), served by Spring WebFlux as static resources.
- **Backend**: Spring Boot 4.x, Spring WebFlux (reactive, Netty), built with Gradle (Kotlin DSL, wrapper).
- **Database**: SQLite, single file at `db/finally.db`, accessed via JDBC (`sqlite-jdbc`) + Spring `JdbcClient`, offloaded from the event loop.
- **Real-time data**: Server-Sent Events via `Flux<ServerSentEvent<...>>`.
- **AI integration**: OpenRouter chat-completions API called with Spring `WebClient`, model `openai/gpt-oss-120b`, Cerebras as inference provider, JSON-schema structured output.
- **Market data**: simulator by default; Massive (Polygon.io) REST polling if `MASSIVE_API_KEY` is set.

### Technology Choices

| Concern | Choice | Rationale |
|---|---|---|
| Language | Java 25 (LTS) | Latest LTS; records, sealed types, pattern matching, `RandomGenerator` |
| Framework | Spring Boot 4.x (latest GA) on Spring Framework 7 | Current generation; Jackson 3, JSpecify null-safety, ProblemDetail |
| Web | Spring WebFlux (Netty) | Natural fit for SSE fan-out; `Flux.interval` streams, non-blocking `WebClient` |
| HTTP client | `WebClient` | Reactive, already on the classpath with WebFlux; used for Massive and OpenRouter |
| Persistence | `sqlite-jdbc` + `JdbcClient` + `TransactionTemplate` | No mature R2DBC SQLite driver exists; JDBC on `Schedulers.boundedElastic()` is simple and correct for a single-user app |
| Schema init | `spring.sql.init` with `schema.sql` / `data.sql` | Idempotent `CREATE TABLE IF NOT EXISTS` + `INSERT OR IGNORE`; no Flyway needed |
| Scheduling | `@Scheduled` (`@EnableScheduling`) | Simulator ticks, Massive polling, 30s snapshots — no extra infrastructure |
| JSON | Jackson 3 (Boot 4 default), `SNAKE_CASE` naming | One consistent wire format for REST, SSE and the LLM schema |
| Validation | `spring-boot-starter-validation` (Jakarta Bean Validation) | Declarative request validation on records |
| Errors | `@RestControllerAdvice` returning `ProblemDetail` (RFC 9457) | Standard error body; frontend reads `detail` |
| Build | Gradle wrapper (`./gradlew`), Kotlin DSL | No global install; concise, type-safe build script; fast incremental builds |
| Tests | JUnit Jupiter, `WebTestClient`, `StepVerifier` (reactor-test), MockWebServer | Standard Spring/Reactor testing stack |

### Why These Choices

| Decision | Rationale |
|---|---|
| SSE over WebSockets | One-way push is all we need; simpler, no bidirectional complexity, universal browser support |
| Static Next.js export | Single origin, no CORS issues, one port, one container, simple deployment |
| SQLite over Postgres | No auth = no multi-user = no need for a database server; self-contained, zero config |
| Single Docker container | Students run one command; no docker-compose, no service orchestration |
| Market orders only | Eliminates order book, limit order logic, partial fills — dramatically simpler portfolio math |

### Why Not Spring AI / R2DBC

- **Spring AI** would work (OpenAI-compatible client pointed at OpenRouter), but OpenRouter-specific fields (`provider.order`, `reasoning.effort`) and strict JSON-schema output are simpler and more transparent as one hand-built `WebClient` request. Fewer moving parts, easier to mock.
- **R2DBC** has no maintained SQLite driver. Blocking JDBC wrapped in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` is the documented pattern for this case.

### Three-Layer Architecture

The backend is split into three layers. Each layer calls only the layer directly below it; `model` records and `config` are shared by all.

```
controller  (presentation)  HTTP/SSE in and out, request validation
    ↓
service     (business)      trade rules, P&L, chat flow, transactions, reactive bridge, scheduled jobs
    ↓
repository  (data access)   SQLite via JdbcClient, in-memory PriceCache, Massive and OpenRouter HTTP clients
```

| Layer | Classes | Rules |
|---|---|---|
| Controller | `HealthController`, `PriceStreamController`, `PortfolioController`, `WatchlistController`, `ChatController`, `ApiExceptionHandler` | Thin: validate input, call one service method, return its result. Request/response records that differ from `model` live in `controller/dto`; `model` records that already match the §8 JSON (e.g. `PriceQuote`, history points) are returned directly. No business logic, never touches a repository or `PriceCache` |
| Service | `PortfolioService`, `WatchlistService`, `MarketService` (price stream, tracked set), `ChatService` (+ prompt builder), `SnapshotService` (30s job), `market/*` sources | All business rules and validation errors. Owns `TransactionTemplate` and wraps blocking repository calls in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` |
| Repository | `UserProfileRepository`, `WatchlistRepository`, `PositionRepository`, `TradeRepository`, `SnapshotRepository`, `ChatMessageRepository`, `PriceCache`, `client/*` | Plain data access, blocking and synchronous (JDBC) or `Mono` (WebClient). No business rules, no transactions |

- Services may call other services (e.g. `ChatService` → `PortfolioService.trade(...)`), so chat trades and REST trades share one code path.
- Domain errors are thrown as exceptions from services (e.g. `TradeRejectedException`) and mapped to `ProblemDetail` in `ApiExceptionHandler`.
- Framework errors (malformed JSON, unknown enum value, Bean Validation failures) must also return `ProblemDetail`: `ApiExceptionHandler` extends WebFlux's `ResponseEntityExceptionHandler`, and overrides the `WebExchangeBindException` handler so `detail` lists the failing fields and messages.
- Tests follow the layers: repositories against a temp-file SQLite, services with real repositories, controllers with `WebTestClient`.

---

## 4. Directory Structure

```
finally/
├── frontend/                       # Next.js TypeScript project (static export)
├── backend/                        # Spring Boot Gradle project
│   ├── build.gradle.kts, settings.gradle.kts
│   ├── gradlew, gradlew.bat, gradle/wrapper/
│   └── src/
│       ├── main/
│       │   ├── java/com/finallyapp/
│       │   │   ├── FinallyApplication.java
│       │   │   ├── controller/     # Presentation layer: REST + SSE controllers, exception handler
│       │   │   │   └── dto/        # Request/response records not already covered by model/
│       │   │   ├── service/        # Business layer: rules, transactions, scheduled jobs
│       │   │   │   └── market/     # MarketDataSource, SimulatorMarketDataSource, MassiveMarketDataSource
│       │   │   ├── repository/     # Data access layer: JDBC repositories, PriceCache
│       │   │   │   └── client/     # External APIs: MassiveClient, LlmClient, OpenRouterLlmClient, MockLlmClient
│       │   │   ├── model/          # Domain records shared by all layers (PriceQuote, Position, Trade, Side, ...)
│       │   │   └── config/         # AppProperties, WebClient beans, DataSource bean (creates DB directory), static/SPA config
│       │   └── resources/
│       │       ├── application.yaml
│       │       ├── chat/
│       │       │   ├── system-prompt.md
│       │       │   └── response-schema.json
│       │       └── db/
│       │           ├── schema.sql  # CREATE TABLE IF NOT EXISTS ...
│       │           └── data.sql    # INSERT OR IGNORE seed data
│       └── test/java/com/finallyapp/...
├── planning/                       # Project-wide documentation for agents
│   ├── PLAN.md                     # This document
│   └── ...                         # Additional agent reference docs
├── scripts/
│   ├── start_mac.sh                # Launch Docker container (macOS/Linux)
│   ├── stop_mac.sh                 # Stop Docker container (macOS/Linux)
│   ├── start_windows.ps1           # Launch Docker container (Windows PowerShell)
│   └── stop_windows.ps1            # Stop Docker container (Windows PowerShell)
├── test/                           # Playwright E2E tests + docker-compose.test.yml
├── db/                             # Volume mount target (finally.db at runtime)
│   └── .gitkeep                    # Directory exists in repo; finally.db is gitignored
├── Dockerfile                      # Multi-stage: Node → JDK build → JRE runtime
├── .env / .env.example             # .env gitignored, .env.example committed
└── .gitignore                      # includes backend/build/, backend/.gradle/
```

Notes:
- The Java base package is `com.finallyapp`. **`finally` is a reserved Java keyword** and cannot be used as a package segment.
- Schema and seed SQL live in `backend/src/main/resources/db/` per Gradle standard source set conventions.
- Packages are organized by layer (controller → service → repository); see §3 Three-Layer Architecture.

### Key Boundaries

- **`frontend/`** is a self-contained Next.js project. It knows nothing about Java. It talks to the backend via `/api/*` endpoints and `/api/stream/*` SSE endpoints. Internal structure is up to the Frontend Engineer agent.
- **`backend/`** is a self-contained Gradle project. It owns all server logic including database initialization, schema, seed data, API routes, SSE streaming, market data, and LLM integration.
- **`db/`** at the top level is the runtime volume mount point. The SQLite file (`db/finally.db`) is created here by the backend and persists across container restarts via Docker volume.
- **`planning/`** contains project-wide documentation, including this plan. All agents reference files here as the shared contract.
- **`test/`** contains Playwright E2E tests and supporting infrastructure (`docker-compose.test.yml`). Unit tests live within `frontend/` and `backend/` respectively, following each framework's conventions.
- **`scripts/`** contains start/stop scripts that wrap Docker commands.

---

## 5. Environment Variables

```bash
# Required for real LLM chat
OPENROUTER_API_KEY=your-openrouter-api-key-here
# Optional; empty -> simulator
MASSIVE_API_KEY=
# "true" -> deterministic mock LLM
LLM_MOCK=false
# Optional; default ../db/finally.db (relative to backend/); container sets /app/db/finally.db.
# Leave commented out: an empty DB_PATH= counts as set and yields an in-memory database.
# DB_PATH=
```

- Comments go on their own lines. Neither Docker `--env-file` nor the `.env` properties import strips inline `# ...`, so it would become part of the value (e.g. `LLM_MOCK` would fail to bind as a boolean).
- `DB_PATH` never appears uncommented with an empty value: Spring uses the `${DB_PATH:...}` default only when the variable is absent, and in Docker an `--env-file` entry overrides the image's `ENV DB_PATH`.

### Loading

- **In Docker**: variables come from `--env-file .env`.
- **Local dev** (running from `backend/`): `application.yaml` imports the root `.env`.
- Bound to a typed `@ConfigurationProperties` record (`AppProperties`) — no scattered `@Value`. The env-var names are mapped explicitly in `application.yaml`:
  ```yaml
  app:
    openrouter:
      api-key: ${OPENROUTER_API_KEY:}
    massive:
      api-key: ${MASSIVE_API_KEY:}
      poll-interval: 15s
    llm-mock: ${LLM_MOCK:false}
    db-path: ${DB_PATH:../db/finally.db}
  spring:
    config:
      import: optional:file:../.env[.properties]
    jackson:
      property-naming-strategy: SNAKE_CASE   # confirm the Boot 4 / Jackson 3 property name in step 2
    datasource:
      url: jdbc:sqlite:${app.db-path}
      hikari:
        maximum-pool-size: 1
    sql:
      init:
        mode: always
        schema-locations: classpath:db/schema.sql
        data-locations: classpath:db/data.sql
    task:
      scheduling:
        pool:
          size: 2      # simulator tick is not delayed by a snapshot write or a Massive poll
  ```

### Behavior

- `MASSIVE_API_KEY` non-empty → `MassiveMarketDataSource`; otherwise → `SimulatorMarketDataSource`. Selected in a single `@Bean` factory method in `config/`.
- `LLM_MOCK=true` → `MockLlmClient`; otherwise → `OpenRouterLlmClient`.

---

## 6. Market Data

### One Interface, Two Implementations

```java
public interface MarketDataSource {
    /** Start producing prices for the given ticker (idempotent). The simulator puts a seed price in PriceCache immediately. */
    void track(String ticker);
    /** Stop producing prices for a ticker. */
    void untrack(String ticker);
}
```

Both implementations write into the shared `PriceCache`. Nothing downstream (SSE, portfolio, frontend) knows which source is active.

### Price Cache

```java
public record PriceQuote(String ticker, double price, double previousPrice,
                         double openPrice, Instant timestamp) {
    @JsonProperty Direction direction();      // UP, DOWN, FLAT
    @JsonProperty double changePercent();     // vs openPrice (daily change %)
}
```

- Derived accessors are neither record components nor `get*` getters, so Jackson skips them unless annotated `@JsonProperty`.
- Wire enums (`Direction`, `Side`, `WatchlistAction`) are lowercase on the wire (`"up"`, `"buy"`, `"add"`): each has a `@JsonValue` method returning `name().toLowerCase()` and a `@JsonCreator` factory accepting any case. Default Jackson enum handling would emit `"BUY"` and reject `"buy"`.

- `PriceCache` is a `@Component` wrapping a `ConcurrentHashMap<String, PriceQuote>`.
- Methods: `update(ticker, price)`, `update(ticker, price, openPrice)`, `get(ticker)`, `all()`, `remove(ticker)`.
- `openPrice` = first price seen for the ticker (simulator, via `update(ticker, price)`) or previous close (Massive, via `update(ticker, price, openPrice)`), used for the watchlist's daily change %.
- `update` rounds prices to 2 decimals before storing, so the SSE stream, trade fills and stored trade prices all use the same cent-precise price. `changePercent()` is rounded to 2 decimals. The simulator keeps its own unrounded GBM state.

### Tracked Tickers

The set of tickers priced by the system = **watchlist ∪ open positions**. A ticker removed from the watchlist keeps being priced while a position in it exists, so portfolio valuation never goes stale. On startup, the backend loads this set from the DB and calls `track()` for each.

One rule for `untrack()`: after a watchlist removal, and after a sell that closes a position, `MarketService` calls `untrack(ticker)` (and `PriceCache.remove`) only if the ticker is in neither the watchlist nor the positions.

### Simulator (Default)

- `@Scheduled(fixedRate = 500)` tick.
- Geometric Brownian Motion per ticker: `S' = S * exp((mu - sigma²/2) dt + sigma sqrt(dt) Z)`, `dt` = 0.5s expressed as a fraction of a trading year (`0.5 / (252 * 6.5 * 3600)`), multiplied by a time-acceleration constant `TIME_SCALE = 100` (one tick simulates ~50s of trading). Without it a $190 stock moves ~2 cents per tick, so many ticks show no change at 2 decimals and sparklines look flat; with it, ~20 cents.
- Correlation via a one-factor model: `Z_i = beta_i * Z_market + sqrt(1 - beta_i²) * Z_i,idio`. Tech names get a higher beta so they move together.
- Random events: each tick, each ticker has a small probability (~0.1%) of a 2–5% jump, up or down.
- Seed prices and per-ticker `mu`/`sigma`/`beta` in a static table (AAPL ~190, GOOGL ~175, MSFT ~420, AMZN ~185, TSLA ~250, NVDA ~120, META ~500, JPM ~200, V ~280, NFLX ~650). Unknown tickers start at a random price in [50, 300] with default parameters.
- Uses `java.util.random.RandomGenerator`; a seeded instance is injectable for deterministic tests.

### Massive API (Optional)

- REST polling (not WebSocket) — simpler, works on all tiers.
- `@Scheduled` with interval from config (`app.massive.poll-interval`, default 15s for the free tier of 5 calls/min; paid tiers can poll every 2–15s).
- One `WebClient` call per poll for all tracked tickers:
  `GET https://api.massive.com/v2/snapshot/locale/us/markets/stocks/tickers?tickers=AAPL,MSFT,...&apiKey=...`
- Response mapped to records; `lastTrade.p` (or `day.c` fallback) → price, `prevDay.c` → `openPrice`.
- Massive JSON is camelCase, while the app's Jackson naming is snake_case (§5). The Massive response records are annotated `@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)` so they map correctly.
- Failures are logged and the cache keeps the last known prices; the next poll retries.

### SSE Streaming

- `GET /api/stream/prices`, `produces = text/event-stream`. Client uses native `EventSource`.
- Implementation: `Flux.interval(Duration.ofMillis(500))` → snapshot `PriceCache.all()` → emit one `ServerSentEvent` per tick whose data is the array of quotes. Each subscriber gets its own interval; no shared sink needed.
- Event data (one event, array of quotes):
  ```json
  [{"ticker":"AAPL","price":190.12,"previous_price":190.05,"open_price":189.40,
    "change_percent":0.38,"direction":"up","timestamp":"2026-10-06T14:00:00.500Z"}]
  ```
- A comment heartbeat is unnecessary since data flows every 500ms. The `retry:` field is set to 1000ms so `EventSource` reconnects quickly.

---

## 7. Database

### SQLite with Idempotent Initialization

- Driver: `org.xerial:sqlite-jdbc`. URL: `jdbc:sqlite:${app.db-path}` (see §5). Default rollback journal; no foreign keys (the schema has none).
- The parent directory of the DB file is created if missing (sqlite-jdbc creates the file, not the directory). This happens inside a custom `DataSource` `@Bean` in `config/`, before it builds the `HikariDataSource`, so the directory exists before any connection is opened (including by `spring.sql.init`).
- `spring.sql.init.mode=always` runs `classpath:db/schema.sql` then `classpath:db/data.sql` on every start (locations set explicitly in §5; Spring's defaults are the classpath root). Both are idempotent: `CREATE TABLE IF NOT EXISTS`, and seed inserts guarded so they run only on an empty database (see Seed Data). Fresh volumes get seeded; existing data, including tickers the user removed, is untouched.
- HikariCP with `maximum-pool-size: 1`: SQLite allows one writer; a single connection serializes access and avoids `SQLITE_BUSY`. Load is a single user, so this is not a bottleneck.

### Reactive Bridge

All repository methods are plain blocking JDBC. Services expose `Mono`/`Flux` by wrapping them:

```java
Mono.fromCallable(() -> repo.findPositions(userId)).subscribeOn(Schedulers.boundedElastic())
```

Multi-statement operations (a trade) run inside `TransactionTemplate.execute(...)` within that callable. **No other DB access may happen while a transaction is open** (e.g. loading portfolio context from another `Mono` inside the transaction): with a pool of one connection, it would block until the Hikari connection timeout. Load unrelated context (e.g. portfolio context for chat) first, then open the transaction. The reads a trade's rules depend on (cash balance, current position) happen inside `TransactionTemplate.execute(...)`, so two concurrent trades cannot both pass the cash or shares check.

### Schema

All tables except `users_profile` include a `user_id` column defaulting to `"default"`; `users_profile` is keyed by the user id itself (`id`). This is hardcoded for now (single-user) but enables future multi-user support without schema migration. Ids are UUID text generated in Java (`UUID.randomUUID()`), except seed rows, which use random hex from SQL; timestamps are ISO-8601 text from `Instant.now().toString()`.

**users_profile** — User state (cash balance)
- `id` TEXT PRIMARY KEY (default: `"default"`)
- `cash_balance` REAL (default: `10000.0`)
- `created_at` TEXT

**watchlist** — Tickers the user is watching
- `id` TEXT PRIMARY KEY
- `user_id` TEXT (default: `"default"`)
- `ticker` TEXT
- `added_at` TEXT
- UNIQUE `(user_id, ticker)`

**positions** — Current holdings (one row per ticker per user)
- `id` TEXT PRIMARY KEY
- `user_id` TEXT (default: `"default"`)
- `ticker` TEXT
- `quantity` REAL (fractional shares supported)
- `avg_cost` REAL
- `updated_at` TEXT
- UNIQUE `(user_id, ticker)`

**trades** — Trade history (append-only log)
- `id` TEXT PRIMARY KEY
- `user_id` TEXT (default: `"default"`)
- `ticker` TEXT
- `side` TEXT (`"buy"` or `"sell"`)
- `quantity` REAL
- `price` REAL
- `executed_at` TEXT

**portfolio_snapshots** — Portfolio value over time (for P&L chart). Recorded every 30 seconds by a scheduled job, and immediately after each trade. A snapshot is skipped while any held position has no price yet, so the chart never shows a false dip.
- `id` TEXT PRIMARY KEY
- `user_id` TEXT (default: `"default"`)
- `total_value` REAL
- `recorded_at` TEXT

**chat_messages** — Conversation history with LLM
- `id` TEXT PRIMARY KEY
- `user_id` TEXT (default: `"default"`)
- `role` TEXT (`"user"` or `"assistant"`)
- `content` TEXT
- `actions` TEXT (JSON — trades executed, watchlist changes made, errors; null for user messages)
- `created_at` TEXT

### Seed Data

- One user profile: `id="default"`, `cash_balance=10000.0`.
- Ten watchlist entries: AAPL, GOOGL, MSFT, AMZN, TSLA, NVDA, META, JPM, V, NFLX.

Seeding runs only when `users_profile` is empty, so a user's watchlist removals survive restarts. `data.sql` inserts the watchlist first (guarded by the profile check), then the profile:

```sql
INSERT INTO watchlist (id, user_id, ticker, added_at)
SELECT lower(hex(randomblob(16))), 'default', column1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
FROM (VALUES ('AAPL'),('GOOGL'),('MSFT'),('AMZN'),('TSLA'),('NVDA'),('META'),('JPM'),('V'),('NFLX'))
WHERE NOT EXISTS (SELECT 1 FROM users_profile);

INSERT OR IGNORE INTO users_profile (id, cash_balance, created_at)
VALUES ('default', 10000.0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));
```

---

## 8. API Endpoints

All JSON is `snake_case`. Money values in responses (cash, prices, values, P&L, percentages) are rounded to 2 decimals in the DTOs; quantities are not rounded. Errors return `ProblemDetail` (`application/problem+json`) with a human-readable `detail`.

### Market Data
| Method | Path | Response |
|---|---|---|
| GET | `/api/stream/prices` | SSE, see §6 |

### Portfolio
| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/portfolio` | — | `Portfolio` |
| POST | `/api/portfolio/trade` | `{"ticker":"AAPL","quantity":5,"side":"buy"}` | `TradeResult` (200) / ProblemDetail (400) |
| GET | `/api/portfolio/history` | — | `[{"total_value":10012.5,"recorded_at":"..."}]` last 24h, ascending by time |

```json
// Portfolio
{"cash_balance":8100.0,"total_value":10050.2,"unrealized_pnl":50.2,
 "positions":[{"ticker":"AAPL","quantity":10,"avg_cost":190.0,"current_price":195.02,
               "market_value":1950.2,"unrealized_pnl":50.2,"pnl_percent":2.64}]}

// TradeResult
{"ticker":"AAPL","side":"buy","quantity":10,"price":190.0,"cash_balance":8100.0}
```

A held position with no price yet (e.g. right after a restart with Massive, before the first poll) returns `current_price`, `market_value`, `unrealized_pnl` and `pnl_percent` as `null`. `total_value` = `cash_balance` + sum of `market_value` over priced positions; `unrealized_pnl` = sum of `unrealized_pnl` over priced positions. Snapshots use the same `total_value`.

### Watchlist
| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/watchlist` | — | `[{"ticker":"AAPL","price":190.1,"change_percent":0.4,"direction":"up"}]` (price fields null until first quote) |
| POST | `/api/watchlist` | `{"ticker":"PYPL"}` | 201 + the new item; 409 if already present |
| DELETE | `/api/watchlist/{ticker}` | — | 204; 404 if absent |

### Chat
| Method | Path | Request | Response |
|---|---|---|---|
| POST | `/api/chat` | `{"message":"Buy 5 AAPL"}` | `ChatReply` |
| GET | `/api/chat/history` | — | `[{"role":"user","content":"Buy 5 AAPL","actions":null,"created_at":"..."}]` last 50, ascending; `actions` has the `ChatReply.actions` shape for assistant messages. Lets the panel restore history on reload |

```json
// ChatReply
{"message":"Bought 5 AAPL at $190.12.",
 "actions":{"trades":[{"ticker":"AAPL","side":"buy","quantity":5,"price":190.12}],
            "watchlist_changes":[{"ticker":"PYPL","action":"add"}],
            "errors":["Sell TSLA 100: insufficient shares (own 0)"]}}
```

### System
| Method | Path | Response |
|---|---|---|
| GET | `/api/health` | `{"status":"ok"}` (simple controller; Actuator not needed) |

### Business Rules (Trades)

- `ticker` trimmed and upper-cased, then must match `^[A-Z.]{1,10}$` (400 otherwise; same rule for `POST /api/watchlist` and LLM actions); `quantity > 0` (fractional allowed); `side` ∈ {`buy`,`sell`}.
- Market orders only: fill price = current `PriceCache` price, no fees; reject with 400 if no price is available yet.
- **Buy**: require `cash >= qty * price`. New avg cost = `(old_qty * old_avg + qty * price) / (old_qty + qty)`.
- **Sell**: require `owned >= qty` (with a small epsilon, `1e-9`). Avg cost unchanged. If remaining quantity ≈ 0, delete the position row.
- Each trade, in one transaction: update cash, upsert/delete position, append to `trades`. After commit, record a portfolio snapshot.
- Buying a ticker not on the watchlist calls `MarketDataSource.track()` (already priced, since a price was required) — no implicit watchlist add.

---

## 9. LLM Integration

### Call Shape

The `cerebras-inference` skill describes a Python LiteLLM call to OpenRouter. The Java equivalent is a direct OpenRouter call via `WebClient`:

```
POST https://openrouter.ai/api/v1/chat/completions
Authorization: Bearer ${OPENROUTER_API_KEY}

{
  "model": "openai/gpt-oss-120b",
  "provider": {"order": ["cerebras"], "require_parameters": true},
  "reasoning": {"effort": "low"},
  "messages": [...],
  "response_format": {
    "type": "json_schema",
    "json_schema": {"name": "finally_response", "strict": true, "schema": { ... }}
  }
}
```

- The JSON schema is a static resource (`chat/response-schema.json`) matching the records below; `choices[0].message.content` is parsed with Jackson into `LlmResponse`.
- Request/response DTOs are Java records.
- `require_parameters: true` makes OpenRouter route only to providers that support every parameter sent (notably `response_format` with a strict schema), instead of silently dropping it on a fallback provider.
- The call has a 30s timeout (`.timeout(Duration.ofSeconds(30))` on the `Mono`). A timeout is handled like any other LLM failure (graceful reply, see Auto-Execution).

```java
record LlmResponse(String message, List<LlmTrade> trades, List<LlmWatchlistChange> watchlistChanges) {}
record LlmTrade(String ticker, Side side, double quantity) {}
record LlmWatchlistChange(String ticker, WatchlistAction action) {}
```

Example LLM output:

```json
{"message":"Buying 10 AAPL.","trades":[{"ticker":"AAPL","side":"buy","quantity":10}],
 "watchlist_changes":[{"ticker":"PYPL","action":"add"}]}
```

With strict mode, every object in the schema lists all its properties in `required` and sets `"additionalProperties": false`. `trades` and `watchlist_changes` are therefore required but may be empty arrays. Verify in step 11 that Cerebras via OpenRouter honors the strict schema.

### Flow (`ChatService.handle(message)`)

1. Load portfolio context (cash, positions with P&L, watchlist with live prices, total value).
2. Load the last 20 `chat_messages`. Each assistant message includes its `actions` JSON, so the model knows what it actually executed and what failed.
3. Build messages: system prompt + portfolio context (as a compact JSON block) + history + new user message.
4. Call `LlmClient.complete(messages)` → `Mono<LlmResponse>`.
5. Apply each watchlist change through `WatchlistService` first, so a newly watched ticker is tracked (and priced, see §6) before any trade in it.
6. Execute each trade through the same `PortfolioService.trade(...)` used by the REST endpoint; collect successes and errors.
7. Persist the user message and the assistant message (with `actions` JSON) to `chat_messages`.
8. Return `ChatReply` (complete JSON, no token streaming — Cerebras is fast enough that a loading indicator suffices).

### Auto-Execution

Trades from the LLM execute automatically, with no confirmation dialog: the money is simulated, it makes for a fluid demo, and it showcases agentic AI — the core theme of the course. A trade that fails validation is reported in `actions.errors` so the assistant's reply can explain it.

If the LLM call fails or returns unparseable JSON, return a `ChatReply` with an apologetic message and the error in `actions.errors` (HTTP 200), and persist it — the chat panel never breaks.

### System Prompt

"You are FinAlly, an AI trading assistant." Analyze portfolio composition, risk concentration and P&L; suggest trades with reasoning; execute trades when the user asks or agrees; manage the watchlist proactively; be concise and data-driven; always respond with the structured JSON. Stored as a resource file `chat/system-prompt.md`.

### Mock Mode (`LLM_MOCK=true`)

`MockLlmClient` returns deterministic responses without network calls, enabling fast, free, reproducible E2E tests and development without an API key.

| User message matches (whole message, trimmed) | Mock response |
|---|---|
| `^buy\s+(\d+(\.\d+)?)\s+([A-Z.]+)$` | message "Mock: buying ..." + one buy trade |
| `^sell\s+(\d+(\.\d+)?)\s+([A-Z.]+)$` | message "Mock: selling ..." + one sell trade |
| `^watch\s+([A-Z.]+)$` | one watchlist add |
| `^unwatch\s+([A-Z.]+)$` | one watchlist remove |
| anything else | fixed message "Mock: I am FinAlly, your AI trading assistant." with no actions |

Patterns are anchored, so `unwatch X` never matches the `watch` rule. Matching is case-insensitive. Trades still go through real validation, so E2E tests exercise the full path.

---

## 10. Frontend Design

### Layout

A single-page application with a dense, terminal-inspired layout. Component architecture and layout system are up to the Frontend Engineer, but the UI includes:

- **Watchlist panel** — grid/table of watched tickers (the SSE stream also carries held-but-unwatched tickers; the panel filters by the watchlist, while positions use every price in the stream) with: ticker symbol, current price (flashing green/red on change), daily change % (`change_percent`), and a sparkline mini-chart (accumulated from SSE since page load)
- **Main chart area** — larger chart for the currently selected ticker, with at minimum price over time. Clicking a ticker in the watchlist selects it here.
- **Portfolio heatmap** — treemap where each rectangle is a position, sized by portfolio weight, colored by P&L (green = profit, red = loss)
- **P&L chart** — line chart of total portfolio value over time, from `/api/portfolio/history`
- **Positions table** — ticker, quantity, avg cost, current price, unrealized P&L, % change
- **Trade bar** — ticker field, quantity field, buy button, sell button. Market orders, instant fill.
- **AI chat panel** — docked/collapsible sidebar. Message input, scrolling conversation history (restored from `/api/chat/history`), loading indicator while waiting for the LLM. Trade executions, watchlist changes and errors shown inline as confirmations.
- **Header** — portfolio total value (updating live), connection status indicator, cash balance

### Technical Notes

- Uses the exact contract in §6 and §8 (snake_case fields, one SSE event carrying an array of quotes).
- `EventSource` for the SSE connection to `/api/stream/prices`.
- Canvas-based charting library preferred (Lightweight Charts or Recharts) for performance.
- Price flash effect: on a new price, briefly apply a CSS class with a background color transition, then remove it.
- All API calls go to the same origin (`/api/*`) — no CORS configuration needed.
- Tailwind CSS with a custom dark theme.

---

## 11. Docker & Deployment

### Multi-Stage Dockerfile

```
Stage 1: node:24-slim (current LTS)
  - Copy frontend/
  - npm ci && npm run build            → frontend/out/

Stage 2: eclipse-temurin:25-jdk
  - Copy backend/ build files first (gradlew, gradle/, build.gradle.kts, settings.gradle.kts), `chmod +x gradlew` (exec bit may be lost when committed from Windows), run `./gradlew dependencies` for layer caching
  - Copy src/, run ./gradlew bootJar -x test → build/libs/finally.jar (`tasks.bootJar { archiveFileName = "finally.jar" }` in build.gradle.kts)

Stage 3: eclipse-temurin:25-jre
  - WORKDIR /app
  - Copy finally.jar
  - Copy frontend/out → /app/static
  - ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/static/  DB_PATH=/app/db/finally.db
  - EXPOSE 8000
  - CMD ["java", "-jar", "/app/finally.jar"]
```

`server.port: 8000` is set in `application.yaml`.

### Static Files and SPA

- WebFlux serves `/app/static/**` and resolves `/` to `index.html` (welcome page).
- `/api/**` routes always take precedence over static resources.
- The app is a single page, so no client-side route fallback is needed. If routes are added later, add a `RouterFunction` that serves `index.html` for unknown non-`/api` GETs.

### Docker Volume

The SQLite database persists via a named Docker volume mapped to `/app/db`:

```bash
docker run -v finally-data:/app/db -p 8000:8000 --env-file .env finally
```

### Start/Stop Scripts

- **`scripts/start_mac.sh`** (macOS/Linux): builds the image if not already built (or if `--build` is passed), runs the container with the volume, port mapping and `.env`, prints the URL, optionally opens the browser.
- **`scripts/stop_mac.sh`**: stops and removes the container. Does NOT remove the volume (data persists).
- **`scripts/start_windows.ps1`** / **`scripts/stop_windows.ps1`**: PowerShell equivalents.

All scripts are idempotent — safe to run multiple times.

### Optional Cloud Deployment

The container can deploy to AWS App Runner, Render, or any container platform. A Terraform configuration for App Runner in `deploy/` is a stretch goal, not part of the core build.

---

## 12. Local Development

```bash
# Backend (from backend/), reads ../.env, writes ../db/finally.db, serves on :8000
./gradlew bootRun

# Frontend dev server (from frontend/), proxies /api to :8000
npm run dev

# Backend tests
./gradlew test
```

Next.js `rewrites` are not supported with `output: 'export'`, so `next.config.ts` applies the `/api` rewrite only in development and `output: 'export'` only for production builds (switch on `process.env.NODE_ENV`).

Verify early that SSE streams through the dev proxy without buffering (events arrive every ~500ms). If it buffers, point `EventSource` at `http://localhost:8000/api/stream/prices` in dev and enable CORS for that one endpoint in a `dev` Spring profile.

Prerequisites: JDK 25, Node 24.

---

## 13. Testing Strategy

### Backend Unit / Slice Tests (`./gradlew test`)

| Area | Tests | Tools |
|---|---|---|
| Simulator | Prices stay positive; GBM step math with a seeded `RandomGenerator`; correlation sign for same-sector tickers over many steps; event jump magnitude in [2%, 5%] | JUnit |
| Massive client | Parses a recorded snapshot JSON into quotes; handles missing fields and HTTP errors | MockWebServer |
| Interface conformance | Both sources populate `PriceCache` after `track()` | JUnit |
| Portfolio | Buy/sell, avg-cost math, sell-all deletes row, insufficient cash, insufficient shares, no-price rejection, selling at a loss | JUnit + temp-file SQLite |
| LLM | Parses valid responses (with/without trades), malformed JSON → graceful reply, failed trade appears in `errors`, mock-mode patterns | MockWebServer, JUnit |
| API | Status codes and response shapes for every endpoint, ProblemDetail on errors | `@SpringBootTest` + `WebTestClient` |
| SSE | First events arrive and contain tracked tickers | `WebTestClient` + `StepVerifier` |

Each test uses a fresh SQLite file in a JUnit `@TempDir` (set via `DB_PATH`) and `LLM_MOCK=true`.

### Frontend Tests (React Testing Library or similar)

- Component rendering with mock data
- Price flash animation triggers correctly on price changes
- Watchlist CRUD operations
- Portfolio display calculations
- Chat message rendering and loading state

### E2E Tests (`test/`)

**Infrastructure**: `test/docker-compose.test.yml` runs the app container (with `LLM_MOCK=true`) plus a Playwright container, keeping browser dependencies out of the production image. The mock patterns in §9 (`buy 5 AAPL`, `watch PYPL`) give chat scenarios deterministic inputs.

**Key Scenarios**:
- Fresh start: default watchlist appears, $10k balance shown, prices are streaming
- Add and remove a ticker from the watchlist
- Buy shares: cash decreases, position appears, portfolio updates
- Sell shares: cash increases, position updates or disappears
- Portfolio visualization: heatmap renders with correct colors, P&L chart has data points
- AI chat (mocked): send a message, receive a response, trade execution appears inline
- SSE resilience: disconnect and verify reconnection

---

## 14. Implementation Plan (Incremental)

Each step ends with a concrete check. Do not start the next step until the check passes.

| # | Step | Validate |
|---|---|---|
| 1 | Generate project at start.spring.io: Java 25, latest Boot 4.x, Gradle - Kotlin; deps `webflux`, `validation`, `jdbc`, `configuration-processor`; add `sqlite-jdbc`, `reactor-test`, `mockwebserver` | `./gradlew test` passes on the empty context test |
| 2 | `application.yaml` (port 8000, `.env` import, snake_case Jackson), `AppProperties`, `GET /api/health` | `curl :8000/api/health` → `{"status":"ok"}` |
| 3 | DataSource bean (creates DB directory), `schema.sql`, `data.sql` | Start twice; DB file exists, 10 watchlist rows, cash 10000, no duplicates |
| 4 | `PriceCache` + `MarketDataSource` + simulator | Unit tests; log shows prices moving |
| 5 | SSE endpoint | `curl -N :8000/api/stream/prices` shows an event every ~500ms |
| 6 | Watchlist service + endpoints, tracking hooked into market source | Add/remove via curl; new ticker appears in SSE |
| 7 | Portfolio service, trade endpoint, `GET /api/portfolio` | Unit tests for all trade rules; curl a buy and a sell |
| 8 | Snapshot job (30s + after trade), `/api/portfolio/history` | History grows; a trade adds a point immediately |
| 9 | Error handling (`ProblemDetail`) | Invalid trade, malformed JSON and `"side":"hold"` → 400 `application/problem+json` with `detail` |
| 10 | `MockLlmClient`, `ChatService`, `/api/chat`, `/api/chat/history` | `buy 5 AAPL` via chat executes a trade |
| 11 | `OpenRouterLlmClient` with structured output | Real call with key returns a parsed reply |
| 12 | Frontend (`frontend/`, §10): Next.js app against the backend via the dev proxy | `npm run dev` with backend running: prices stream, trade and chat work; `npm run build` produces `frontend/out/` |
| 13 | Static serving + Dockerfile | `docker run` → frontend loads at `:8000`, prices stream |
| 14 | E2E tests in `test/` | Playwright suite green with `LLM_MOCK=true` |
| 15 | *Deferred:* Massive client (optional source) | MockWebServer test; manual run with a real key if available |

---

## 15. Key Design Decisions

1. **Exact contract** — every endpoint and the SSE event have a fixed JSON shape (§6, §8), shared by frontend and backend agents.
2. **Daily change %** — quotes carry `open_price` and `change_percent`.
3. **Tracked set = watchlist ∪ positions** — removing a held ticker from the watchlist never stops its pricing.
4. **Trade before first price** — explicit 400 rejection.
5. **Selling the full position** — row deleted; epsilon for fractional comparisons.
6. **LLM failure / malformed output** — graceful 200 reply with the error, never a broken chat.
7. **Chat history on reload** — `GET /api/chat/history`.
8. **Error format** — RFC 9457 `ProblemDetail` everywhere.
9. **Deterministic mock LLM** — anchored pattern table usable by E2E tests.
10. **SQLite concurrency** — single-connection pool, transactions around trades, no DB access inside an open transaction.
11. **Duplicate watchlist add / missing ticker delete** — 409 / 404.
12. **Three-layer architecture** — controller → service → repository, one-way dependencies, business rules and transactions only in services (§3).

---

## 16. Review Notes (2026-10-06)

All review issues, questions and simplifications are resolved and incorporated above:

- Seeding only on an empty DB; Massive camelCase mapping; snake_case in YAML; `users_profile` keyed by `id`; chat history shape; `resources/chat/` in tree.
- Null price fields for unpriced positions; "daily change" label kept; simulator reset on restart accepted; 2-decimal rounding; 24h history; actions included in LLM history.
- Watchlist panel filters the SSE stream; ticker validation `^[A-Z.]{1,10}$`; dev SSE proxy check with `:8000` fallback.
- Simplified: no `foreign_keys`/WAL, no fixed seed ids, `ChatReply` failures only in `errors`, single `@Bean` source selection, `model` records reused as DTOs, no root `docker-compose.yml`, Massive deferred.
