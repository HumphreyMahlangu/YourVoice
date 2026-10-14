# CLAUDE.md — VoteTrust (repo: YourVoice)

Portfolio project: a secure online-voting **simulation** for South Africa. Code, packages and docs all say
**VoteTrust**; the GitHub repo is `HumphreyMahlangu/YourVoice`. It isn't a real election system, and the
code should never imply it is.

- **Backend**: Java 21, Spring Boot 3.3.4, Spring Security (stateless JWT), JPA/Hibernate, Flyway, PostgreSQL 16.
  Package root: `io.github.humphreymahlangu.votetrust` in `src/main/java`.
- **Frontend**: `frontend/` (React 19, TypeScript 6, Vite 8, react-router 8, Vitest, oxlint). No UI library, plain CSS.
- **Deploy**: API + Postgres → Render (`render.yaml`, Docker, auto-deploys after CI passes). Frontend → Vercel
  (`frontend/vercel.json`). There is no other deployment target (Azure was removed on 2026-10-14).

Deeper reference (read the relevant one before non-trivial work):

| File | Covers |
| --- | --- |
| [docs/claude/domain.md](docs/claude/domain.md) | Data model, election/contest lifecycle state machine, eligibility rules, anonymous voting flow, hash-chain ledger |
| [docs/claude/api.md](docs/claude/api.md) | Every endpoint with auth + status codes, exception→HTTP mapping, rate limiting, config/env vars |
| [docs/claude/frontend.md](docs/claude/frontend.md) | Routes, auth session model, page/data-loading patterns, VotePage state machine, styling |
| [docs/claude/testing-and-deployment.md](docs/claude/testing-and-deployment.md) | Test layout, tests that assert on file contents, CI, Docker/Render/Vercel |
| [docs/claude/known-issues.md](docs/claude/known-issues.md) | Open gaps and gotchas (what was fixed on 2026-10-14 is listed there too) |

## Commands

```bash
# Backend (repo root). Needs the env vars below; the tests set their own.
./mvnw clean verify                      # what CI runs
./mvnw test -Dtest=DeploymentArtifactTest # one class
./mvnw spring-boot:run                    # API on :8080 (Swagger at /swagger-ui.html, spec at /api-docs)
docker compose --env-file .env up --build # Postgres + API (copy .env.example → .env first)

# Frontend (cd frontend)
npm run dev      # Vite on :5173 (strictPort)
npm test         # vitest run
npm run lint     # oxlint
npm run build    # tsc -b && vite build
```

Gate on Maven's exit code (`./mvnw … > log; echo $?`), not on `grep` output.

Required runtime env: `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (plus `SPRING_DATASOURCE_URL`, or
`_HOST/_PORT/_NAME`), `VOTETRUST_JWT_SECRET`, `VOTETRUST_ID_HASH_PEPPER`, `VOTETRUST_VOTE_CREDENTIAL_PEPPER`.
All three secrets must be ≥32 chars and independent of each other. If any is missing, startup fails validation.

**Docker is required for ~half the backend tests.** Testcontainers 1.21.4 is the minimum that works with Docker
Engine 29, so don't downgrade it. If a shell predates the user's `docker` group membership, run Maven through
`sg docker -c "./mvnw clean verify"`. Integration tests use Testcontainers with
`@Testcontainers(disabledWithoutDocker = true)`, so without Docker they are *silently skipped*, not failed.
A green local run without Docker doesn't prove those tests pass. CI sets
`VOTETRUST_TEST_DATASOURCE_URL/USERNAME/PASSWORD` to use a service Postgres instead of a container.

## Architecture (backend)

`controller` → `service` (transactional, all business rules) → `repository` (Spring Data) → Postgres.
Also: `entity` (JPA + enums), `dto` (Java records), `security` (JWT, rate limiter, login throttle, HMAC hashing,
blind signatures, request IDs, property records), `observability` (metrics), `exception` (+ `GlobalExceptionHandler`),
`config` (`SecurityConfig`, `ClockConfig`, `OpenApiConfig`, `SchedulerLockConfig`, `LoggingProperties`).

Key services: `AuthService` + `AuthTokenService` (refresh tokens), `AdminBootstrapService`, `AdminAccountService`,
`VoterRegistrationService`, `VotingService` (blind-signature issuance, ballot casting), `BallotSigningKeyService`,
`VoteHashChainService` (internal ledger), `LedgerPublicationService` (shuffled public ledger), `TallyAuditService`,
`ElectionLifecycleService` + `ElectionLifecycleCoordinator` (ShedLock-guarded scheduler),
`AdminElectionManagementService`, `SecurityAuditService`. `ContestEligibility` is the one eligibility rule.

## Conventions to follow

- **No Lombok.** Entities have hand-written getters, a `protected` no-arg constructor, a public constructor, and
  intent-named mutators (`transitionTo`, `markUsed`, `advanceTo`, `updateDetails`). There are no generic setters.
- **Constructor injection only**, with explicit constructors.
- **DTOs are records.** Map entities with static factories on the response record (`ElectionResponse.from(…)`,
  `ContestResponse.from(contest, options)`), not private mapper methods.
- **Time comes from the injected `java.time.Clock` bean** (`Instant.now(clock)`). Tests swap in a
  `@Primary Clock.fixed(...)`. Everything is UTC.
- **Errors:** throw a domain exception from `exception/` and add a handler to `GlobalExceptionHandler` for new
  types. The catch-all keeps Spring 4xx statuses and turns anything else into a logged 500 carrying a request ID.
- **Race-free creation:** for "create if missing, then lock", use a native `INSERT … ON CONFLICT DO NOTHING`
  followed by a `FOR UPDATE` read (see `LedgerStateRepository.insertIfAbsent`, `VotingRightRepository`,
  `ContestSigningKeyRepository`). After locking, `entityManager.refresh` the entity if the same transaction might
  already have loaded it. A cached instance hides other transactions' commits; this bug actually happened.
- **Strings get normalised in services:** emails `trim().toLowerCase(Locale.ROOT)`, names trimmed, district codes upper-cased.
- **Schema changes = a new Flyway migration** `V{n}__snake_case.sql` (latest is **V14**). `ddl-auto=validate`, and
  applied migrations are never edited.
- **Every controller needs OpenAPI annotations** (`@Tag`, `@ApiResponses`, and `@Operation` with summary and
  description on every GET/POST/PUT/PATCH/DELETE handler; `bearerAuth` where authenticated).
  `OpenApiAnnotationCoverageTest` has a hard-coded controller list, so add new controllers to it.
- **Metrics** go through `observability/VoteTrustMetrics`. Never tag a metric with a contest option, because
  count-over-time per option leaks choices.
- **Imports:** the codebase mixes ASCII and case-insensitive ordering. Keep a file's existing order and slot new imports in.
- Frontend conventions are in frontend.md.

## Invariants you must not break

These are what the project exists to demonstrate. Treat a change that weakens one as a design decision for the
user to make, not a refactor.

1. **Ballot anonymity.** `BallotLedgerEntry`, `PublishedBallotEntry`, `AnonymousVotingCredential` and
   `ContestSigningKey` never reference user/voter/registration/voting-right. `VotingRight` stores no timestamps
   and only the *blinded* token hash. `BallotCastResponse` has no receipt fields. `POST /ballots` is
   unauthenticated: the blind signature is the authorisation. `BallotAnonymitySchemaTest` checks field names.
2. **Unlinkable credentials.** The server only ever sees blinded tokens at issuance and unblinded (token,
   signature) pairs at casting. Never log, store or correlate either with the voter. Exactly one signing key per contest.
3. **One vote per voter per contest.** One `VotingRight` signature per (voter, contest), spent tokens unique,
   locks on voting right / ledger state / election. `ConcurrentVotingIntegrationTest` proves it; keep it green.
4. **Ledger formats are load-bearing.** Internal hash `votetrust-ledger-v1|…` (exact instant) and `-v2|…`
   (UTC day), and public hash `votetrust-public-ledger-v1|…`. The shared vectors in Java, TS and Postman tests
   pin the hash constructions. Never change an existing version; add a new one. The public ledger order must
   stay independent of cast order.
5. **Raw secrets are never stored or logged.** SA IDs and spent tokens are peppered HMACs; refresh tokens are SHA-256.
6. **Lifecycle is automatic.** Admins can cancel, and while DRAFT they can rename, reschedule and edit the ballot
   (until `registrationEndAt`). They can't open or close stages.
7. **Election registration uses the voter-profile district.**
8. **Results, audit and ledger stay hidden until** election `COMPLETED`, contest `CLOSED`, and `now > votingEndAt`.

## Tests that read repo files (easy to break by accident)

`DeploymentArtifactTest` and `PostmanArtifactTest` do substring assertions on `Dockerfile`, `.dockerignore`,
`compose.yaml`, `application.properties`, `render.yaml`, `.github/workflows/ci.yml` (including the frontend job),
`DEPLOYMENT.md`, and the Postman JSON. Editing any of those files can fail the build, so update the assertion in the same change.
The Postman JSON is generated by `postman/generate-verified-postman.js`, so edit the generator and re-run it with
`node`, not the JSON.

## Working notes

- `frontend/.env.development` and `.env.production` are **tracked** even though `.gitignore` lists them
  (gitignore has no effect on tracked files). They hold only the public API URL.
- The developer runs Docker locally. To experiment without touching their database, use a separate Compose
  project (see testing-and-deployment.md); the developer's own local DB is already on V14.
- `target/` and `frontend/node_modules`, `frontend/dist` are local build output.
