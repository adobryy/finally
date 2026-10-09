# Review Log

## 2026-10-06 — Review of changes since last commit (6b568a9)

**Changed files:** `planning/PLAN-V2.md` (new, untracked). No code changes.

**Summary:** PLAN-V2.md is a complete Java/Spring WebFlux backend specification that keeps V1's product, schema and API paths, and adds an exact API contract plus an incremental build plan. It is consistent with V1 overall. The issues below should be fixed before agents start building.

### Issues

1. **`DB_PATH` default is inconsistent (§5 vs §12).** §5 says the default is `db/finally.db`, but §12 runs the backend from `backend/` and says it writes `../db/finally.db`. A `db/finally.db` default relative to `backend/` would create a second DB in `backend/db/`. Fix: set the default to `../db/finally.db` for local dev and keep `/app/db/finally.db` in Docker.
2. **`.env` keys are not mapped to `AppProperties`.** Importing `.env` makes `OPENROUTER_API_KEY` etc. available as properties, but the plan never says how they bind to the typed `AppProperties` record. Fix: state the mapping in `application.yaml`, e.g. `app.openrouter.api-key: ${OPENROUTER_API_KEY:}`, `app.massive.api-key: ${MASSIVE_API_KEY:}`, `app.llm-mock: ${LLM_MOCK:false}`.
3. **Mock LLM patterns overlap.** `unwatch PYPL` also matches `watch <TICKER>` unless the match is anchored or `unwatch` is checked first. Fix: anchor the patterns (`^watch\s+`, `^unwatch\s+`) or define the evaluation order.
4. **Jar name is assumed.** ~~The Dockerfile copies `target/finally.jar`, which requires `<finalName>finally</finalName>` in `pom.xml`.~~ Resolved: build switched to Gradle; §11 now sets `tasks.bootJar { archiveFileName = "finally.jar" }`.
5. **Scheduler thread sharing.** All `@Scheduled` jobs share Spring's default single-thread scheduler, so a slow Massive poll or a snapshot DB write can delay the 500ms simulator tick. Low impact; set `spring.task.scheduling.pool.size: 2` or note it as acceptable.
6. **Strict JSON-schema output needs to be verified with Cerebras.** OpenRouter strict mode requires `additionalProperties: false` and all fields listed as required. The plan covers the required arrays but not `additionalProperties`. Fix: add it to the schema description and verify in step 11.

### Unverified external details (already flagged in the plan)

- Latest Spring Boot 4.x minor version — confirm at start.spring.io (step 1).
- Massive snapshot endpoint URL and field names — confirm in step 12.
- OpenRouter `reasoning.effort` parameter name — confirm in step 11.

### Verdict

Sound plan. Fix items 1–4 before implementation starts; 5–6 can be handled in their build steps.

## 2026-10-06 — Review: Maven to Gradle switch in PLAN-V2.md

**Changed files:** `planning/PLAN-V2.md`, `planning/REVIEW.md` (both untracked).

- All Maven references replaced (sections 3, 4, 11, 12, 13, 14, 15); grep confirms none remain.
- Dockerfile stage 2 caches dependencies via `./gradlew dependencies` before copying `src/`, then `bootJar -x test`; jar name fixed with `archiveFileName = "finally.jar"`, so stage 3's `COPY` path is consistent.
- `./gradlew bootRun` uses `backend/` as working dir, so `../.env` import and `../db/` paths still hold.
- Minor: stage 2 base image `eclipse-temurin:25-jdk` must allow `gradlew` to be executable (`chmod +x gradlew` if checked in from Windows without the exec bit).

**Verdict:** Consistent. Earlier open issues 1-3, 5, 6 still apply.

## 2026-10-06 — Resolution of open issues in PLAN-V2.md

- Issue 1 (DB_PATH default): fixed — default is `../db/finally.db` relative to `backend/` (§5, §7); Docker sets `/app/db/finally.db`.
- Issue 2 (.env to AppProperties mapping): fixed — explicit `app.*` mapping in `application.yaml` shown in §5.
- Issue 3 (mock pattern overlap): fixed — anchored regexes with separate `watch` / `unwatch` rows (§9).
- Issue 4 (jar name): fixed earlier via `bootJar.archiveFileName`.
- Issue 5 (scheduler thread sharing): fixed — `spring.task.scheduling.pool.size: 2` (§5).
- Issue 6 (strict JSON schema): fixed — `additionalProperties: false` and all properties required; verification kept in step 11 (§9).
- Gradle exec bit: fixed — `chmod +x gradlew` in Dockerfile stage 2 (§11).

**Open:** none in the plan. Stop hook agent still lacks write permission for this file in "don't ask" mode (needs a user-added permission rule).

---

## 2026-10-06 — Final Session Review: PLAN-V2.md Complete and Validated

**Session ID:** `71bedc1d-ad22-45b6-af20-1f4e43cdf34c`

**Changed files:** `planning/PLAN-V2.md` (new, untracked). No committed code changes since last commit (`6b568a9 Updated docs and settings`).

### Scope of Work

This session produced a complete Java/Spring Boot backend specification (PLAN-V2.md) for the FinAlly trading workstation. V2 retains the UX, schema, and API paths from the Python-based V1 plan while replacing the backend entirely with a modern reactive stack.

### Content Summary

#### 1. **Architecture & Tech Stack** (§1–3)
- Single Docker container, single port (8000), Spring Boot 4.x on Spring WebFlux/Netty.
- Reactive web layer using `Flux` for SSE streaming and `WebClient` for external APIs.
- SQLite single-file database via JDBC (R2DBC has no maintained SQLite driver; blocking JDBC wrapped in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` is the documented pattern).
- Jackson 3 with `SNAKE_CASE` naming (consistent with V1 frontend expectations).

#### 2. **Market Data & SSE** (§4–6)
- Simulator (default) implements Geometric Brownian Motion with correlation and random jump events.
- Massive API (Polygon.io) as optional real-time source; selected via `MASSIVE_API_KEY` presence.
- PriceCache as shared mutable state; both sources populate it identically.
- SSE streaming via `Flux.interval(500ms)` emitting array of price quotes every tick.

#### 3. **Database & Persistence** (§7)
- Idempotent initialization: `CREATE TABLE IF NOT EXISTS` + `INSERT OR IGNORE` for schema and seed.
- HikariCP single-connection pool (SQLite allows one writer; serialization avoids `SQLITE_BUSY`).
- Tracked set = watchlist ∪ open positions (ensures pricing continues for held tickers removed from watchlist).
- Schema identical to V1 (users_profile, watchlist, positions, trades, portfolio_snapshots, chat_messages with UUIDs and ISO-8601 timestamps).

#### 4. **API Endpoints** (§8)
- Exact contract specified: `/api/stream/prices` (SSE), `/api/portfolio`, `/api/portfolio/trade`, `/api/portfolio/history`, `/api/watchlist`, `/api/chat`, `/api/chat/history`, `/api/health`.
- All JSON in `snake_case`.
- Error responses as RFC 9457 `ProblemDetail` (application/problem+json).
- Trade rules: buy/sell validation, average cost calculation, position deletion when quantity ≈ 0, no implicit watchlist adds.

#### 5. **LLM Integration** (§9)
- `WebClient` → OpenRouter `/chat/completions` with `openai/gpt-oss-120b` model and Cerebras as inference provider.
- Structured JSON output via strict `json_schema` response format.
- System prompt stored as resource file; message history persisted to `chat_messages`.
- Mock mode (`LLM_MOCK=true`): deterministic pattern-based responses (buy/sell/watch/unwatch) for testability and reproducible E2E tests.
- Graceful error handling: LLM failures or malformed output return HTTP 200 with error details in `actions.errors`.

#### 6. **Docker & Local Dev** (§11–12)
- Multi-stage Dockerfile: Node 24 (frontend build) → Temurin 25 JDK (backend build) → Temurin 25 JRE (runtime).
- Gradle wrapper (no global install); `./gradlew bootJar -x test` builds the jar; static files copied into runtime image.
- Local dev: `./gradlew bootRun` from `backend/` (reads `../.env`, writes `../db/finally.db`, listens on :8000).
- Frontend dev server runs separately, proxying `/api/*` to backend via Next.js rewrites.

#### 7. **Testing Strategy** (§13)
- Backend unit/slice tests cover: simulator math, Massive client parsing, portfolio trade rules, LLM parsing and mock patterns, API endpoints and response shapes, SSE event delivery.
- All tests use fresh SQLite (temp file), `LLM_MOCK=true`, and standard Spring/Reactor tooling (JUnit, `WebTestClient`, `StepVerifier`, `MockWebServer`).
- E2E tests (Playwright) use deterministic mock patterns and `docker-compose.test.yml`.

#### 8. **Implementation Plan** (§14)
- 14 incremental steps, each with a concrete validation check.
- Builds from health endpoint → database → market data → SSE → watchlist → portfolio → snapshots → error handling → mock LLM → real LLM → optional Massive → Docker → E2E tests.

#### 9. **V1 Gaps Addressed** (§15)
- Response shapes now fully specified (no guessing).
- Daily change % grounded in `open_price` and `change_percent` fields.
- Watched tickers that have open positions remain priced even after removal from watchlist.
- Trade-before-first-price is explicitly rejected (400).
- Selling full positions deletes the row; epsilon-based floating-point comparisons guard against precision errors.
- LLM failures and malformed output never break the chat (graceful 200 with error details).
- Chat history endpoint enables reload recovery.
- RFC 9457 error format (`ProblemDetail`) everywhere.
- Mock LLM behavior is fully deterministic (usable by agents and E2E tests).
- SQLite concurrency handled by single-connection pool + WAL + transactions.
- Duplicate watchlist adds return 409; missing ticker deletes return 404.

### Key Strengths

1. **Completeness**: Every component has a detailed specification — no ambiguity about what needs building or how it should work.
2. **Backward compatibility**: API paths, database schema, and response fields are identical to V1, allowing frontend agents to reuse existing code.
3. **Correctness of tradeoffs**: The choice to use blocking JDBC on `boundedElastic()` instead of R2DBC is well-reasoned (R2DBC has no maintained SQLite driver) and documented.
4. **Testability**: Mock LLM mode with deterministic patterns, fresh SQLite per test, and a clear 14-step build plan make this highly implementable by agents.
5. **Production readiness**: WAL, connection pooling, error handling, RFC 9457 errors, and Docker multi-stage build are all production-grade.

### Minor Observations

1. **External dependency verification deferred**: Latest Spring Boot 4.x minor version, Massive snapshot endpoint URL, and OpenRouter parameter names are noted as to-be-confirmed at step start (acceptable for a spec).
2. **Frontend integration**: Spec assumes frontend is already built and static-exported; no detail on Next.js build or deployment, but that's in-scope for V1 and unchanged here.
3. **Authentication**: None. Suitable for a single-user demo app and mentioned in V1 as out-of-scope.

### Verdict

**PLAN-V2.md is production-ready and implementable.** All critical decisions are explained, all endpoints are contracted, all business rules are clear, and the 14-step plan is achievable. Issues from earlier reviews (DB paths, `.env` mapping, mock patterns, scheduler thread pool, strict JSON schema) have all been resolved. No blocker issues remain. Agents can begin implementation at step 1 without waiting for clarifications.

### Status

- ✅ PLAN-V2.md written and validated.
- ✅ All earlier findings from session reviews incorporated.
- ✅ Ready for agent implementation starting at §14 step 1.
- ⏸ Awaiting user save of `.claude/settings.json` (IDE edit pending) — not blocking this phase.

---

## 2026-10-07 — Comprehensive review of PLAN.md (Java/Spring rewrite, uncommitted on `start`)

**Scope:** `planning/PLAN.md` now holds the full Java 25 / Spring Boot 4 WebFlux spec, replacing the committed Python/FastAPI plan. Earlier issues (DB path, `.env` mapping, mock pattern anchoring, jar name, scheduler pool, strict schema, `chmod +x gradlew`) are confirmed fixed. Note: the 2026-10-06 "Final Session Review" above mentions WAL, but the plan has since dropped WAL on purpose (§16). That entry is out of date, not a defect.

Overall the plan is coherent, layered cleanly, and buildable step by step. The items below are new findings. Each one would cause a real bug or a stalled step if left as written.

### High: will cause bugs

1. **An empty `DB_PATH=` silently gives an in-memory database (§5, §11).** `.env.example` lists `DB_PATH=` with an empty value. Spring uses the default in `${DB_PATH:../db/finally.db}` only when the property is *absent*. An empty value counts as present, so the URL becomes `jdbc:sqlite:`, which is an in-memory DB. In Docker it is worse: `--env-file .env` overrides the image's `ENV DB_PATH=/app/db/finally.db` with the empty string. The result is no persistence and no error. Fix: leave `DB_PATH` out of `.env.example`, or comment it out (`# DB_PATH=`), and say so in §5.
2. **Inline comments in `.env` become part of the values (§5).** Neither Docker `--env-file` nor the `.properties` import strips trailing `# ...` comments. If `.env.example` copies the §5 block as written, `LLM_MOCK` becomes `false   # "true" -> ...` and fails boolean binding, and the API key gets corrupted. Fix: in `.env.example`, put comments on their own lines only.
3. **Records and enums will not serialize to the §6/§8 contract by default.**
   - `PriceQuote.direction()` and `changePercent()` are plain methods, not record components or `get*` getters, so Jackson does not emit `direction` or `change_percent`. Fix: annotate them with `@JsonProperty`, or make them components that are computed in a static factory.
   - The enums `Direction`, `Side` and `WatchlistAction` serialize as `UP`/`BUY`/`ADD`, but the contract and the LLM schema use lowercase. Incoming `"side":"buy"` will fail to deserialize. Fix: add `@JsonValue` lowercase plus a matching `@JsonCreator` on each enum (or one shared mixin). State this in §6 and §8 so agents do not each invent their own fix.
4. **Read-then-write race in trades (§7 Reactive Bridge).** "Load what you need first, then open the transaction" can be read as "read cash and position before the transaction". Two concurrent trades (for example a REST trade and a chat trade) would then both pass the cash check. Fix: say explicitly that the trade's own reads (cash, existing position) happen *inside* `TransactionTemplate.execute`, on the same connection. Only unrelated context loading (such as the chat portfolio context) happens before.
5. **"Watch and buy" via chat cannot succeed (§8, §9 Flow).** A ticker can be bought only if it already has a price. ChatService runs trades (step 5) *before* watchlist changes (step 6). Even with the order swapped, a new ticker has no price until the next tick or poll. So "add PYPL and buy 10" always fails, and so does a plain "buy 10 PYPL". Fix: (a) apply watchlist changes before trades, and (b) have `SimulatorMarketDataSource.track()` put the seed price into `PriceCache` right away. With Massive the trade still fails until the next poll, which is acceptable and reported in `errors`. The sentence "Buying a ticker not on the watchlist calls `track()` (already priced, since a price was required)" describes a case that can barely happen. Replace it with one rule: the tracked set is recomputed after watchlist changes and after a sell that closes a position, and `untrack` happens only when a ticker is neither watched nor held. The plan never says when `untrack` runs after a full sell.
6. **`DbDirectoryInitializer` must run before the DataSource and SQL init (§7).** A plain `@Component` is not guaranteed to run before `spring.sql.init` opens the first connection, and sqlite-jdbc fails if the directory is missing. Fix: create the parent directory inside a custom `DataSource` `@Bean` method in `config/` (built from `DataSourceProperties`). Spring's script initializer depends on the DataSource, so this ordering is guaranteed.

### Medium: clarify before the step that needs it

7. **Simulator volatility is too low for the demo (§6).** With real-time `dt = 0.5/(252*6.5*3600)` and annual sigma around 0.3, each tick moves about 0.009%, roughly 2 cents on a $190 stock. After 2-decimal rounding many ticks are FLAT (no flash), and a 10-minute sparkline moves about 0.3%. That works against "visually stunning". Fix: add a time-acceleration factor (for example `dt` multiplied by about 50–100), or define per-tick sigma directly. Pin it in the static table so the step 4 tests stay deterministic.
8. **Where 2-decimal rounding happens is unclear (§6 vs §8).** §8 rounds "in the DTOs", but SSE and `/api/watchlist` return `PriceQuote` directly (§3 allows this), so they would be unrounded. Fix: round once when writing to `PriceCache`, and keep the simulator's internal GBM state unrounded. Then trade fill prices also match what the user saw.
9. **The `PriceCache.update(ticker, price)` signature cannot carry the Massive `openPrice`** (`prevDay.c`). Fix: `update(ticker, price, openPrice)`, where the simulator passes the first seen price.
10. **Framework errors must also be `ProblemDetail`.** Malformed JSON, a bad enum value, or a Bean Validation failure raise `ServerWebInputException` or `WebExchangeBindException`. A bare `@RestControllerAdvice` does not cover these. Fix: have `ApiExceptionHandler` extend the reactive `ResponseEntityExceptionHandler`, or set `spring.webflux.problemdetails.enabled: true`. Otherwise the frontend cannot rely on `detail` (§8).
11. **The OpenRouter call needs a timeout and a provider pin (§9).** `WebClient` has no response timeout by default, so a stalled call hangs the chat. Fix: add `.timeout(Duration.ofSeconds(30))` and map it to the graceful-failure reply. Also add `"require_parameters": true` (and optionally `"allow_fallbacks": false`) to `provider`, so OpenRouter does not route to a provider that ignores `response_format`.
12. **`total_value` definition (§8).** State it explicitly as `cash + sum(market_value of priced positions)`. The current sentence only says what is summed.
13. **The frontend is not in the implementation sequence (§14).** Step 13 validates "frontend loads at `:8000`", but no step builds the frontend. Fix: add a frontend track, either parallel to steps 5–10 against the §6/§8 contract or as a step before 13, with its own check (for example, `npm run build` produces `out/index.html` and the watchlist renders against a running backend).

### Low: notes for implementers

14. **Jackson 3 packages.** Databind classes moved to `tools.jackson.*` (for example `tools.jackson.databind.PropertyNamingStrategies`), while annotations such as `@JsonNaming` and `@JsonProperty` stay in `com.fasterxml.jackson.annotation`. Mention this in §6, since the Massive records need both.
15. **MockWebServer.** Use the current `com.squareup.okhttp3:mockwebserver3` artifact (JUnit 5 friendly) rather than the legacy `mockwebserver` (CLAUDE.md: latest APIs).
16. **Test DB isolation (§13).** `@TempDir` together with `@SpringBootTest` needs a static `@TempDir` and `@DynamicPropertySource` for `app.db-path`. Spring's context cache shares one DB across test classes with the same config, so tests must not assume an empty DB. Alternatively use `@DirtiesContext`, at the cost of speed.
17. **Watchlist ordering.** `GET /api/watchlist` has no defined order. Specify `ORDER BY added_at`, so seed order is kept and new tickers go last.
18. **Snapshot retention.** About 2,880 rows per day are written and never pruned. This is harmless for a demo. Optionally delete rows older than 7 days in the 30s job.
19. **Step numbering.** Step 12 is "built after step 14". Moving it to the end of the table as step 15 (marked optional) would read more clearly.

### Verified as correct

- Java 25 and Node 24 are the current LTS lines. Boot 4 / Framework 7 support Java 25.
- The SQLite `SELECT ... FROM (VALUES ...)` seed with `column1` is valid. The seeding guard keeps user removals across restarts.
- `spring.sql.init.mode: always` is required, since SQLite is not detected as an embedded DB. It is present.
- A single-connection Hikari pool with the "no other DB access inside a transaction" rule is correct for SQLite.
- The `.env` import resolves `${OPENROUTER_API_KEY:}` and the other placeholders, and real environment variables take precedence over the imported file.
- WebFlux serves `index.html` as the welcome page from `spring.web.resources.static-locations`.
- Anchored, case-insensitive mock regexes have no overlap.

### Verdict

The plan is sound and ready to implement once items 1–6 are fixed in the text. All six are one- or two-sentence edits. Items 7–13 can be settled in the step that first touches them (4, 4, 4, 9, 11, 7, 13).
