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
Also: `entity` (JPA + enums), `dto` (Java records, request validation via Jakarta annotations),
`security` (JWT filter, rate limiter, HMAC hashing, config-property records), `exception` (+ `GlobalExceptionHandler`),
`config` (`SecurityConfig`, `ClockConfig`, `OpenApiConfig`).

Key services: `AuthService`, `AdminBootstrapService`, `VoterRegistrationService`, `VotingService`,
`VoteHashChainService`, `TallyAuditService`, `ElectionLifecycleService` + `ElectionLifecycleCoordinator` (scheduler),
`AdminElectionManagementService`, `SecurityAuditService`.

## Conventions to follow

- **No Lombok in practice.** It's in the pom but nothing uses it. Entities have hand-written getters,
  a `protected` no-arg constructor, a public constructor, and intent-named mutators (`transitionTo`, `markUsed`,
  `advanceTo`). There are no generic setters.
- **Constructor injection only**, explicit constructors (no `@Autowired` fields, no `@RequiredArgsConstructor`).
- **DTOs are records.** Services map entities to response records by hand in private `toXxxResponse` methods
  (some duplication across services is the existing style; there's no mapper library).
- **Time comes from the injected `java.time.Clock` bean** (`Instant.now(clock)`), never `Instant.now()` alone.
  Tests swap in a `@Primary Clock.fixed(...)` via a nested `@TestConfiguration`. Everything is UTC.
- **Errors**: throw a domain exception from `exception/` and let `GlobalExceptionHandler` map it to
  `ApiErrorResponse`. If you add an exception type, add a handler. See api.md for the mapping table.
- **Strings get normalised in services**: emails `trim().toLowerCase(Locale.ROOT)`, names trimmed, district codes upper-cased.
- **Schema changes = new Flyway migration** `src/main/resources/db/migration/V{n}__snake_case.sql` (latest is V10).
  `ddl-auto=validate`, so the entity and the migration must match exactly. Never edit an applied migration.
- **Every controller needs OpenAPI annotations**: class-level `@Tag` (name + description) and `@ApiResponses`;
  every handler needs `@Operation(summary, description)`; authenticated endpoints need `bearerAuth`.
  `OpenApiAnnotationCoverageTest` enforces this with a hard-coded controller list, so add new controllers to it.
- Frontend conventions are in frontend.md (one `api/*.ts` module per resource, `apiRequest<T>`, AbortController in effects).

## Invariants you must not break

These are what the project exists to demonstrate. Treat a change that weakens one as a design decision for the
user to make, not a refactor.

1. **Ballot anonymity.** `BallotLedgerEntry` and `AnonymousVotingCredential` must never reference
   user/voter/registration/voting-right. `VotingRight` stores no timestamps. `BallotCastResponse` must not return
   ledger index/hashes/ids (no "receipt"). `BallotAnonymitySchemaTest` checks these field names.
   `POST /api/v1/ballots` is deliberately unauthenticated: the credential *is* the auth.
2. **One vote per voter per contest.** Uniqueness constraints plus pessimistic locks
   (`VotingRight`, credential `...ForUpdate`, `LedgerState ...ForUpdate`, `Election findByIdForUpdate`).
3. **Hash-chain integrity.** The `VoteHashChainService.calculateHash` payload format (`votetrust-ledger-v1|...`) is
   load-bearing. Changing it invalidates every existing ledger's audit.
4. **Raw secrets are never stored or logged.** SA ID numbers and voting credentials are stored only as peppered
   HMAC-SHA-256. The raw credential exists once, in the issuance response.
5. **Lifecycle is automatic.** Admins can only *cancel* an election. They can't open or close registration or voting,
   and they can't change contest status. Ballot configuration is allowed only while the election is `DRAFT` and
   `now < registrationEndAt`.
7. **Election registration uses the voter-profile district.** Contest eligibility depends on it, so never let a
   request choose a different district.
6. **Results, audit and ledger stay hidden until** election `COMPLETED`, contest `CLOSED`, *and* `now > votingEndAt`.

## Tests that read repo files (easy to break by accident)

`DeploymentArtifactTest` and `PostmanArtifactTest` do substring assertions on `Dockerfile`, `.dockerignore`,
`compose.yaml`, `application.properties`, `render.yaml`, `.github/workflows/ci.yml`, `DEPLOYMENT.md`, and the
Postman JSON. Editing any of those files can fail the build, so update the assertion in the same change.
The Postman JSON is generated by `postman/generate-verified-postman.js`, so edit the generator and re-run it with
`node`, not the JSON.

## Working notes

- `frontend/.env.development` and `.env.production` are **tracked** even though `.gitignore` lists them
  (gitignore has no effect on tracked files). They hold only the public API URL.
- `HELP.md` is the stale Spring Initializr stub (it links Boot 4.1 docs; the project uses 3.3.4). Ignore it.
- `target/` and `frontend/node_modules`, `frontend/dist` are local build output.
