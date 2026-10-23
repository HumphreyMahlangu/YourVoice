# Testing, CI and deployment

## Backend test suite (`src/test/java/.../yourvoice`)

| Kind | Classes | Needs Docker/Postgres? |
| --- | --- | --- |
| Pure unit (Mockito / plain) | `service/*ServiceTest`, `SouthAfricanIdNumberValidatorTest`, `ElectionLifecycleCoordinatorTest`, `security/{Jwt,IdentityHash,RateLimit,LoginThrottle,BlindSignature}ServiceTest`, `LedgerPublicationServiceTest` | no |
| Convention / artifact checks | `documentation/OpenApiAnnotationCoverageTest`, `entity/BallotAnonymitySchemaTest`, `deployment/DeploymentArtifactTest`, `deployment/PostmanArtifactTest` | no |
| Full-stack integration (`@SpringBootTest` + MockMvc) | `controller/*IntegrationTest`, `YourVoiceApplicationTests` | **yes** |
| Real-port integration (`RANDOM_PORT` + `java.net.http.HttpClient`) | `controller/ForwardedClientAddressIntegrationTest`. Use this style for anything that happens in Tomcat valves (e.g. `X-Forwarded-For`), because MockMvc bypasses them | **yes** |
| DB-specific | `database/FlywayPostgreSqlMigrationIntegrationTest`, `service/ElectionLifecyclePostgreSqlIntegrationTest` | **yes** |
| Concurrency | `service/ConcurrentVotingIntegrationTest`: threads released by a latch against real Postgres (same token cast twice, double issuance, many first ballots, concurrent publication, scheduler vs cancel) | **yes** |
| Observability | `controller/ObservabilityIntegrationTest` (`@AutoConfigureObservability`, since metrics are off in tests by default): request IDs, framework 4xx statuses, admin-only Prometheus, stuck gauge | **yes** |

Integration-test recipe (copy it for new ones):
- `@SpringBootTest` (+ `properties = {...}` for overrides such as bootstrap enabled or CORS origin),
  `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Testcontainers(disabledWithoutDocker = true)`,
  `extends PostgreSqlTestContainerSupport`.
- `PostgreSqlTestContainerSupport` uses the CI DB if `YOURVOICE_TEST_DATASOURCE_URL` is set; otherwise it starts one
  shared `postgres:16-alpine` container. Flyway runs and Hibernate validates.
- `@BeforeEach void cleanDatabase() { DatabaseCleaner.clean(jdbcTemplate); }` truncates every table except
  `flyway_schema_history`, so new tables need no test changes.
- Blind-signature flows: `support/BlindBallotClient.blind(contestId, modulus, exponent)` plays the voter
  (`ballotToken`, `blindedToken`, `unblind(blindSignature)`).
- Fixed time: a nested `@TestConfiguration` with a `@Primary Clock.fixed(FIXED_NOW, UTC)` bean.
- Tokens: autowire `JwtService` and call `generateAccessToken(account)` instead of going through login.
- `src/test/resources/application-test.properties` supplies the test secrets and sets
  `yourvoice.lifecycle.scheduling-enabled=false`, so tests drive the lifecycle explicitly.

**Without Docker, the integration tests are skipped, not failed.** If they show as skipped although Docker is
running, check the log for `Could not find a valid Docker environment`. Common causes: the shell isn't in the
`docker` group yet (use `sg docker -c "./mvnw clean verify"` or log in again), or Testcontainers is too old for
the Docker Engine (Docker 29 needs ≥ 1.21.4; the project uses 1.21.4). The full suite (141 tests) passed locally
on 2026-10-14.

**Connection budget:** every cached Spring test context owns a Hikari pool. `application-test.properties` keeps
`minimum-idle=1` and `src/test/resources/spring.properties` caps the context cache at 8, so the suite fits
PostgreSQL's default 100 connections, locally and in CI. A new `@SpringBootTest` configuration (different
properties or `@TestConfiguration`) is a new context, so reuse existing configurations where you can.

Gate commits on Maven's exit code, not on `grep` output: `./mvnw … > log; echo $?`.

### Tests that assert on non-Java files

`DeploymentArtifactTest` checks substrings in:
- `Dockerfile` (temurin 21 jdk/jre jammy, `USER yourvoice`, HEALTHCHECK on readiness, OCI title label)
- `.dockerignore`: the **exact** line list
- `compose.yaml`: required-secret `:?` syntax, `read_only`, `no-new-privileges`, `cap_drop`
- `application.properties`: the exact datasource URL expression (env URL, falling back to HOST/PORT/NAME parts),
  username/password placeholders with **no defaults**, graceful shutdown, `server.forward-headers-strategy`, readiness group
- `render.yaml`: docker runtime, readiness health check, `autoDeployTrigger: checksPass`, dashboard-managed secrets (`sync: false`, no `generateValue`),
  CORS origins `sync: false`, bootstrap disabled
- `.github/workflows/ci.yml`: action versions, the mvnw command, compose config, docker build, the frontend job
- `DEPLOYMENT.md` (required env vars, compose smoke test, the Render section, CI gates)

`PostmanArtifactTest` checks that both Postman collections contain specific paths and strings, and that every
`{{var}}` is defined in the matching environment file. Regenerate them with `node postman/generate-verified-postman.js`.

So when you change deployment config, update the assertion in the same change.

## CI (`.github/workflows/ci.yml`)

Runs on push or PR to `main`. Job `test`: a Postgres 16 service, JDK 21 temurin, `./mvnw -B clean verify`; on
failure it publishes surefire reports, then runs `docker compose --env-file .env.example config` and
`docker build`. Job `frontend`: Node 24, `npm ci`, lint, test, build.

`keep-alive.yml` (optional) pings the API every 10 minutes when the repository variable `RENDER_KEEPALIVE_URL`
is set, so Render's free tier doesn't put the lifecycle scheduler to sleep during an election.

There's no deploy workflow. Render deploys itself from GitHub: `autoDeployTrigger: checksPass` in `render.yaml`
waits for the commit's GitHub checks (this CI) to pass. Vercel builds the frontend from its own Git integration.
The optional Heroku app deploys from the connected GitHub branch; `BUILD_FRONTEND=true` activates the Maven
profile that builds the frontend into the Spring Boot JAR.

## Deployment targets

| Target | Files | Notes |
| --- | --- | --- |
| Local Docker | `compose.yaml`, `.env.example` | Postgres 16 + hardened API container (read-only FS, tmpfs /tmp, cap_drop ALL). `.env.example` enables bootstrap for local use. |
| **Render (API + DB)** | `render.yaml` | Blueprint: free web service `votetrust-api` from the Dockerfile + free Postgres `votetrust-db` (both keep their pre-rename names on purpose). Datasource injected as HOST/PORT/NAME/USER/PASSWORD via `fromDatabase`, which is why `application.properties` builds the JDBC URL from parts. The JWT secret, both peppers and `YOURVOICE_CORS_ALLOWED_ORIGINS` are `sync: false`: set in the Render dashboard, never generated or overwritten by a sync. CORS must include the Vercel origin. Health check: `/actuator/health/readiness`. Render injects `PORT`. Deploys only after CI passes. URL: `https://votetrust-api.onrender.com`. |
| **Vercel (frontend)** | `frontend/vercel.json`, `frontend/.env.production` | SPA rewrite. The API URL is baked in at build time from `VITE_API_BASE_URL`. |
| **Heroku (full app + DB)** | `system.properties`, `pom.xml`, `HEROKU.md` | Java 21 buildpack, optional `heroku-frontend` Maven profile activated by `BUILD_FRONTEND=true`, and Heroku Postgres. Spring serves the frontend and API from one web dyno. The frontend uses the same origin when `VITE_API_BASE_URL` is unset. |

Dockerfile: multi-stage (`mvnw dependency:go-offline` then `package -DskipTests`), runtime JRE 21 as the non-root
`yourvoice` user, `JAVA_OPTS` passthrough, curl healthcheck. `.dockerignore` whitelists only build inputs.

### First-admin bootstrap on a deployed instance

Set `YOURVOICE_ADMIN_BOOTSTRAP_ENABLED=true` and a ≥32-char `YOURVOICE_ADMIN_BOOTSTRAP_TOKEN`, redeploy, then call
`POST /api/v1/admin/bootstrap` with header `X-YourVoice-Bootstrap-Token` and body `{email, password}`. Then set
enabled back to false and redeploy. Create further admins from the app (`/admin/accounts`).

### Manual end-to-end check

Use the Postman pair `postman/YourVoice.verified.postman_collection.json` and
`YourVoice.verified-local.postman_environment.json`. They create an election with short real-time windows
(120s configuration grace, 180s registration, 90s voting by default) and wait for the scheduler to advance it.
The voting steps fetch the ballot key and blind and unblind in the Postman sandbox (BigInt + CryptoJS). See
`postman/README.md`.

To test against a disposable stack without touching the developer's local database, use a separate Compose
project: `APP_PORT=18080 POSTGRES_PORT=15432 YOURVOICE_ADMIN_BOOTSTRAP_ENABLED=true YOURVOICE_ADMIN_BOOTSTRAP_TOKEN=<32+ chars> docker compose -p yourvoice-e2e --env-file .env up -d`,
then `docker compose -p yourvoice-e2e down -v`. Shell variables override `.env` values.
