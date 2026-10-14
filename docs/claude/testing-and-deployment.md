# Testing, CI and deployment

## Backend test suite (`src/test/java/.../votetrust`)

| Kind | Classes | Needs Docker/Postgres? |
| --- | --- | --- |
| Pure unit (Mockito / plain) | `service/*ServiceTest`, `SouthAfricanIdNumberValidatorTest`, `ElectionLifecycleCoordinatorTest`, `security/{Jwt,IdentityHash,RateLimit}ServiceTest` | no |
| Convention / artifact checks | `documentation/OpenApiAnnotationCoverageTest`, `entity/BallotAnonymitySchemaTest`, `deployment/DeploymentArtifactTest`, `deployment/PostmanArtifactTest` | no |
| Full-stack integration (`@SpringBootTest` + MockMvc) | `controller/*IntegrationTest`, `VoteTrustApplicationTests` | **yes** |
| Real-port integration (`RANDOM_PORT` + `java.net.http.HttpClient`) | `controller/ForwardedClientAddressIntegrationTest`. Use this style for anything that happens in Tomcat valves (e.g. `X-Forwarded-For`), because MockMvc bypasses them | **yes** |
| DB-specific | `database/FlywayPostgreSqlMigrationIntegrationTest`, `service/ElectionLifecyclePostgreSqlIntegrationTest` | **yes** |

Integration-test recipe (copy it for new ones):
- `@SpringBootTest` (+ `properties = {...}` for overrides such as bootstrap enabled or CORS origin),
  `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Testcontainers(disabledWithoutDocker = true)`,
  `extends PostgreSqlTestContainerSupport`.
- `PostgreSqlTestContainerSupport` uses the CI DB if `VOTETRUST_TEST_DATASOURCE_URL` is set; otherwise it starts one
  shared `postgres:16-alpine` container. Flyway runs and Hibernate validates.
- `@BeforeEach` wipes tables with `repository.deleteAll()` in **FK-safe order**: ledger entries → ledger states →
  credentials → voting rights → lifecycle events → options → contests → registrations → voter profiles → elections →
  districts → user accounts. Add any new table into this chain in every integration test that has it.
- Fixed time: a nested `@TestConfiguration` with a `@Primary Clock.fixed(FIXED_NOW, UTC)` bean.
- Tokens: autowire `JwtService` and call `generateAccessToken(account)` instead of going through login.
- `src/test/resources/application-test.properties` supplies the test secrets and sets
  `votetrust.lifecycle.scheduling-enabled=false`, so tests drive the lifecycle explicitly.

**Without Docker, 50 of 94 tests are skipped, not failed.** If the integration tests show as skipped although Docker
is running, check the log for `Could not find a valid Docker environment`. Common causes: the shell isn't in the
`docker` group yet (use `sg docker -c "./mvnw clean verify"` or log in again), or Testcontainers is too old for the
Docker Engine. Docker 29 needs Testcontainers ≥ 1.21.4; the project uses 1.21.4. The full suite (94 tests) passed
locally on 2026-10-14.

### Tests that assert on non-Java files

`DeploymentArtifactTest` checks substrings in:
- `Dockerfile` (temurin 21 jdk/jre jammy, `USER votetrust`, HEALTHCHECK on readiness, OCI title label)
- `.dockerignore`: the **exact** line list
- `compose.yaml`: required-secret `:?` syntax, `read_only`, `no-new-privileges`, `cap_drop`
- `application.properties`: the exact datasource URL expression (env URL, falling back to HOST/PORT/NAME parts),
  username/password placeholders with **no defaults**, graceful shutdown, `server.forward-headers-strategy`, readiness group
- `render.yaml`: docker runtime, readiness health check, `autoDeployTrigger: checksPass`, generated secrets,
  CORS origins `sync: false`, bootstrap disabled
- `.github/workflows/ci.yml`: action versions, the mvnw command, compose config, docker build
- `DEPLOYMENT.md` (required env vars, compose smoke test, the Render section, CI gates)

`PostmanArtifactTest` checks that both Postman collections contain specific paths and strings, and that every
`{{var}}` is defined in the matching environment file. Regenerate them with `node postman/generate-verified-postman.js`.

So when you change deployment config, update the assertion in the same change.

## CI (`.github/workflows/ci.yml`)

Runs on push or PR to `main`: a Postgres 16 service, JDK 21 temurin, `./mvnw -B clean verify`. On failure it
publishes surefire reports to the job summary and uploads them as an artifact. It also runs
`docker compose --env-file .env.example config` and `docker build`. **The frontend isn't built or tested in CI.**

There's no deploy workflow. Render deploys itself from GitHub: `autoDeployTrigger: checksPass` in `render.yaml`
waits for the commit's GitHub checks (this CI) to pass. Vercel builds the frontend from its own Git integration.

## Deployment targets

| Target | Files | Notes |
| --- | --- | --- |
| Local Docker | `compose.yaml`, `.env.example` | Postgres 16 + hardened API container (read-only FS, tmpfs /tmp, cap_drop ALL). `.env.example` enables bootstrap for local use. |
| **Render (API + DB)** | `render.yaml` | Blueprint: free web service from the Dockerfile + free Postgres `votetrust-db`. Datasource injected as HOST/PORT/NAME/USER/PASSWORD via `fromDatabase`, which is why `application.properties` builds the JDBC URL from parts. JWT and peppers come from `generateValue: true`. `VOTETRUST_CORS_ALLOWED_ORIGINS` is `sync: false`, so it's set in the Render dashboard and must include the Vercel origin. Health check: `/actuator/health/readiness`. Render injects `PORT`. Deploys only after CI passes. URL: `https://votetrust-api.onrender.com`. |
| **Vercel (frontend)** | `frontend/vercel.json`, `frontend/.env.production` | SPA rewrite. The API URL is baked in at build time from `VITE_API_BASE_URL`. |

Dockerfile: multi-stage (`mvnw dependency:go-offline` then `package -DskipTests`), runtime JRE 21 as the non-root
`votetrust` user, `JAVA_OPTS` passthrough, curl healthcheck. `.dockerignore` whitelists only build inputs.

### First-admin bootstrap on a deployed instance

Set `VOTETRUST_ADMIN_BOOTSTRAP_ENABLED=true` and a ≥32-char `VOTETRUST_ADMIN_BOOTSTRAP_TOKEN`, redeploy, then call
`POST /api/v1/admin/bootstrap` with header `X-VoteTrust-Bootstrap-Token` and body `{email, password}`. Then set
enabled back to false and redeploy.

### Manual end-to-end check

Use the Postman pair `postman/VoteTrust.verified.postman_collection.json` and
`VoteTrust.verified-local.postman_environment.json`. They create an election with short real-time windows
(120s configuration grace, 180s registration, 90s voting by default) and wait for the scheduler to advance it.
See `postman/README.md`.
