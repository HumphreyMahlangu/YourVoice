# REST API reference

Base path `/api/v1`. JSON only. Swagger UI at `/swagger-ui.html`, spec at `/api-docs`.
Auth header: `Authorization: Bearer <jwt>`. JWTs are HS256 (JJWT 0.12), `sub` = user UUID, claims `email` and `role`,
issuer checked, 15-minute default lifetime. **No refresh tokens and no revocation.** Every request re-loads the
user from the DB (`AccountUserDetailsService.loadUserById`), so a disabled account is rejected immediately.

## Endpoints

| Method | Path | Access | Success | Notable errors | Controller → service |
| --- | --- | --- | --- | --- | --- |
| POST | `/auth/register` | public, rate-limited `auth` | 201 `AuthResponse` | 400 validation, 404 district, 409 dup email/ID, 422 ID invalid/underage/non-citizen | `AuthController` → `AuthService` |
| POST | `/auth/login` | public, rate-limited `auth` | 200 `AuthResponse` | 401 (same message for unknown email / wrong password / disabled) | `AuthController` → `AuthService` |
| GET | `/auth/me` | any authenticated | 200 `UserAccountResponse` | 401 | `AuthController` |
| POST | `/admin/bootstrap` | public + header token, rate-limited `admin-bootstrap` | 201 `AuthResponse` | 401 token, 403 disabled, 409 exists | `AdminBootstrapController` |
| POST | `/admin/voting-districts` | ADMIN | 201 `VotingDistrictResponse` | 409 dup code | `AdminElectionManagementController` |
| POST | `/admin/elections` | ADMIN | 201 `ElectionResponse` | 409 bad windows / dup name | 〃 |
| PATCH | `/admin/elections/{id}/status` `{status}` | ADMIN | 200 `ElectionResponse` | 409 if status ≠ CANCELLED or election COMPLETED | 〃 → `ElectionLifecycleService.cancelElection` |
| GET | `/admin/elections/{id}/lifecycle-events` | ADMIN | 200 list | 404 | 〃 |
| POST | `/admin/elections/{id}/contests` | ADMIN | 201 `ContestResponse` | 409 locked (not DRAFT, or registration window ended)/type/scope/dup | 〃 |
| POST | `/admin/elections/{id}/contests/{cid}/options` | ADMIN | 201 `ContestOptionResponse` | 409 locked/dup | 〃 |
| PATCH | `/admin/elections/{id}/contests/{cid}/status` | ADMIN | 200 only if unchanged | 409 otherwise | 〃 |
| GET | `/admin/security-audit-events?limit=50` | ADMIN | 200 list (limit clamped to 1..100) | | `AdminSecurityAuditController` |
| GET | `/voting-districts` | public | 200 list (sorted province, municipality, ward, name) | | `VotingDistrictController` |
| GET | `/elections` | public | 200 list (sorted registrationStartAt desc) | | `ElectionController` → `ElectionService` |
| GET | `/elections/{id}` | public | 200 | 404 | 〃 |
| POST | `/elections/{id}/registrations` body optional (`{votingDistrictId?}`) | authenticated (any role, but needs a voter profile) | 201 `ElectionRegistrationResponse` (always the profile district) | 404, 409 closed/dup, 422 district ≠ profile | 〃 → `VoterRegistrationService` |
| GET | `/me/registrations` | authenticated | 200 list | | `MyRegistrationController` |
| GET | `/elections/{id}/contests` | public | 200 list incl. options | 404 | `ContestController` → `ContestService` |
| POST | `/elections/{id}/contests/{cid}/credentials` | authenticated, rate-limited `credential-issuance` | 201 `VotingCredentialResponse` | 404, 409 closed/already issued, 422 ineligible | `ContestController` → `VotingService` |
| POST | `/ballots` | **public** (credential in body), rate-limited `ballot-submission` | 201 `BallotCastResponse` | 401 bad/expired credential, 404 option, 409 closed / "already been used" | `BallotController` → `VotingService` |
| GET | `/elections/{id}/contests/{cid}/results` | public after close | 200 `ContestResultResponse` | 404, 409 not yet available | `ContestResultController` → `TallyAuditService` |
| GET | `/elections/{id}/contests/{cid}/audit` | public after close | 200 `ContestAuditResponse` | 〃 | 〃 |
| GET | `/elections/{id}/contests/{cid}/ledger` | public after close | 200 list | 〃 | 〃 |

Also public: `/actuator/health`, `/actuator/health/{readiness,liveness}`. `/actuator/info` requires auth.

Role enforcement: `SecurityConfig` URL rules (`/api/v1/admin/**` → `hasRole('ADMIN')` except POST bootstrap;
GET `/elections/**` and `/voting-districts/**` public), plus `@PreAuthorize("hasRole('ADMIN')")` on
`AdminElectionManagementController`. The VOTER role is **not** enforced server-side on registration or credential
endpoints. An admin simply fails there because they have no voter profile or registration.

## Error format and mapping

Every error body is `ApiErrorResponse { timestamp, status, error, message, path, fieldErrors }`. `fieldErrors`
(field → first message) is populated only for bean-validation failures. The frontend relies on this shape.

| Exception | HTTP |
| --- | --- |
| `MethodArgumentNotValidException` | 400 "Request validation failed" + fieldErrors |
| `MethodArgumentTypeMismatchException` (e.g. bad UUID in path) | 400 |
| `HttpMessageNotReadableException` (bad JSON / bad enum, which lists the allowed values) | 400 |
| `InvalidCredentialsException`, `InvalidVotingCredentialException`, `InvalidBootstrapTokenException` | 401 |
| Spring Security auth failure (`RestAuthenticationEntryPoint`) | 401 "Authentication is required" |
| `AdminBootstrapException`, access denied (`RestAccessDeniedHandler`) | 403 |
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException`, `DataIntegrityViolationException`, `RegistrationClosedException`, `VotingClosedException`, `ResultsUnavailableException`, `ElectionLifecycleException` | 409 |
| `EligibilityException` | 422 |
| Rate limit (written directly by `RateLimitingFilter`) | 429 + `Retry-After` |

## Request validation highlights

- Passwords (register and bootstrap): 12–128 chars, at least one lower-case letter, one upper-case letter and one digit.
- `southAfricanIdNumber`: `\d{13}` at the DTO level, then the full validator in the service.
- District `code`: `^[A-Za-z0-9-]+$`, max 32. `wardNumber` and `displayOrder`: 1–9999.
- `votingCredential`: not blank, max 128, trimmed before hashing.

## Rate limiting

`RateLimitingFilter` (before the JWT filter) applies to **POST only** on four buckets: `auth` (login and register),
`admin-bootstrap`, `credential-issuance` (regex on the path), and `ballot-submission`. `RateLimitService` is a
fixed-window counter in an in-memory `ConcurrentHashMap` keyed by `bucket:request.getRemoteAddr()`. Allowed responses
set `X-RateLimit-Remaining`. Blocked requests are audited as `RATE_LIMIT_BLOCKED`, and an audit failure can't turn the
429 into a 500. The limiter is in-memory, so it's per-instance.

**Client IP behind a proxy:** `server.forward-headers-strategy=native` (env `SERVER_FORWARD_HEADERS_STRATEGY`) turns on
Tomcat's `RemoteIpValve`. When the TCP peer is a private, loopback or CGNAT address (Tomcat's default `internalProxies`),
`getRemoteAddr()` becomes the right-most untrusted `X-Forwarded-For` entry. That's what both the rate limiter and the
audit `clientIp` use. Direct public clients can't spoof it. If Render's proxy ever connects from a public address,
the valve does nothing and every client shares the proxy IP again. Check this by looking at the audit events after
a deploy. MockMvc doesn't run valves; see `ForwardedClientAddressIntegrationTest`.

Both filters are `@Component`s, so `SecurityConfig` registers `FilterRegistrationBean`s with `enabled=false` to stop
Spring Boot auto-registering them a second time outside the security chain. Keep that if you add another filter.

## CORS

Origins come from `VOTETRUST_CORS_ALLOWED_ORIGINS` (comma-separated). The default is
`http://localhost:5173,http://localhost:3000`. Methods are **GET, POST, PATCH, OPTIONS only** (add PUT or DELETE
here if you ever add such endpoints). Headers: Authorization, Content-Type, Accept, X-Requested-With.
No credentials or cookies.

## Configuration properties (`application.properties`)

| Property | Env var | Default |
| --- | --- | --- |
| `server.port` | `SERVER_PORT`, then `PORT` (Render) | 8080 |
| `server.forward-headers-strategy` | `SERVER_FORWARD_HEADERS_STRATEGY` | `native` |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` or `SPRING_DATASOURCE_HOST/PORT/NAME` | `jdbc:postgresql://localhost:5432/votetrust` |
| `spring.datasource.username/password` | `SPRING_DATASOURCE_USERNAME/PASSWORD` | **required** |
| `votetrust.security.jwt.secret` | `VOTETRUST_JWT_SECRET` | **required, ≥32** |
| `votetrust.security.jwt.issuer` | `VOTETRUST_JWT_ISSUER` | `votetrust-api` |
| `votetrust.security.jwt.access-token-expiration-minutes` | `VOTETRUST_JWT_ACCESS_TOKEN_EXPIRATION_MINUTES` | 15 |
| `votetrust.security.identity-hash-pepper` | `VOTETRUST_ID_HASH_PEPPER` | **required, ≥32** |
| `votetrust.security.vote-credential-pepper` | `VOTETRUST_VOTE_CREDENTIAL_PEPPER` | **required, ≥32** |
| `votetrust.security.cors.allowed-origins` | `VOTETRUST_CORS_ALLOWED_ORIGINS` (on Render: dashboard-managed, `sync: false`) | localhost:5173, :3000 |
| `votetrust.security.rate-limit.{enabled,window-seconds,auth-limit,bootstrap-limit,credential-limit,ballot-limit}` | `VOTETRUST_RATE_LIMIT_*` | true, 60, 100, 20, 100, 100 |
| `votetrust.admin.bootstrap.{enabled,token}` | `VOTETRUST_ADMIN_BOOTSTRAP_ENABLED/TOKEN` | false, empty |
| `votetrust.lifecycle.scheduling-enabled` | `VOTETRUST_LIFECYCLE_SCHEDULING_ENABLED` | true |
| `votetrust.lifecycle.poll-interval-ms` | `VOTETRUST_LIFECYCLE_POLL_INTERVAL_MS` | 5000 |

Property classes live in `security/` (`JwtProperties`, `IdentityHashProperties`, `VoteCredentialProperties`,
`CorsProperties`, `AdminBootstrapProperties`, `RateLimitProperties`). They are registered via
`@EnableConfigurationProperties` on `SecurityConfig`, and most are `@Validated`, so bad values fail at startup.

Rotating either pepper makes every existing hash unverifiable: ID duplicate detection stops working and outstanding
voting credentials stop matching. Rotating the JWT secret only logs everyone out.
