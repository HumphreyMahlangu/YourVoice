# REST API reference

Base path `/api/v1`. JSON only. Swagger UI at `/swagger-ui.html`, spec at `/api-docs`.

**Auth:** `Authorization: Bearer <access JWT>`. The access token is HS256, 15 minutes by default, `sub` = user
id. The user is re-loaded on every request, so disabling an account takes effect immediately. Login, register
and bootstrap also return a **refresh token**: opaque, 12 hours by default, stored hashed, and rotated on every
`/auth/refresh`. Presenting an already-rotated token revokes its whole family. `/auth/logout` revokes the family.
Access tokens are not revocable and expire on their own.

## Endpoints

| Method | Path | Access | Success | Notable errors |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | public, `auth` bucket | 201 `AuthResponse` | 400, 404 district, 409 duplicate (one generic message for email and ID), 422 ID invalid or under 16 |
| POST | `/auth/login` | public, `auth` bucket | 200 `AuthResponse` | 401 (same message for every failure), **429 + Retry-After** when the email is throttled |
| POST | `/auth/refresh` `{refreshToken}` | public, `auth` bucket | 200 `AuthResponse` (new pair) | 401 invalid, expired or reused |
| POST | `/auth/logout` `{refreshToken}` | public, `auth` bucket | 204 (idempotent) | |
| GET | `/auth/me` | authenticated | 200 | 401 |
| POST | `/admin/bootstrap` | public + `X-VoteTrust-Bootstrap-Token` | 201 `AuthResponse` | 401, 403 disabled, 409 |
| GET/POST | `/admin/accounts` | ADMIN | 200 list / 201 | 409 email exists |
| POST | `/admin/voting-districts` | ADMIN | 201 | 409 duplicate code |
| PUT / DELETE | `/admin/voting-districts/{id}` | ADMIN | 200 / 204 | 409 geography change or delete while in use |
| POST | `/admin/elections` | ADMIN | 201 | 409 bad windows or duplicate name |
| PUT | `/admin/elections/{id}` | ADMIN | 200 | 409 not DRAFT, bad windows, registration end in the past |
| PATCH | `/admin/elections/{id}/status` `{status: CANCELLED}` | ADMIN | 200 | 409 other status or already completed |
| GET | `/admin/elections/{id}/lifecycle-events` | ADMIN | 200 | 404 |
| POST | `/admin/elections/{id}/contests` | ADMIN | 201 | 409 locked, type, scope or duplicate |
| PUT / DELETE | `/admin/elections/{id}/contests/{cid}` | ADMIN | 200 / 204 | 409 locked or not DRAFT |
| POST | `/admin/elections/{id}/contests/{cid}/options` | ADMIN | 201 | 409 |
| PUT / DELETE | `/admin/elections/{id}/contests/{cid}/options/{oid}` | ADMIN | 200 / 204 | 409 |
| PATCH | `/admin/elections/{id}/contests/{cid}/status` | ADMIN | 200 only when unchanged | 409 |
| GET | `/admin/security-audit-events?limit=50` | ADMIN | 200 (limit 1–100) | |
| GET | `/voting-districts?page&size` | public | 200 array + `X-Total-Count` | 400 bad paging |
| GET | `/elections?page&size` | public | 200 array + `X-Total-Count` | |
| GET | `/elections/{id}` | public | 200 | 404 |
| POST | `/elections/{id}/registrations` (body optional) | authenticated | 201 (profile district) | 404, 409 closed or duplicate, 422 district ≠ profile |
| GET | `/me/registrations?page&size` | authenticated | 200 array + `X-Total-Count` | |
| GET | `/elections/{id}/contests` | public | 200 incl. options (one options query in total) | 404 |
| GET | `/elections/{id}/contests/{cid}/ballot-key` | public | 200 `BallotKeyResponse` | 404, 409 before voting opens |
| POST | `/elections/{id}/contests/{cid}/credentials` `{blindedToken}` | authenticated, `credential-issuance` bucket | 201 `{blindSignature, keyFingerprint, expiresAt}` | 400 token outside key range, 404, 409 closed or already issued (a different blinded token), 422 ineligible |
| POST | `/ballots` `{contestId, contestOptionId, ballotToken, signature}` | **public**, `ballot-submission` bucket | 201 `{accepted}` | 401 bad signature, 404, 409 closed or "already been used" |
| GET | `/elections/{id}/contests/{cid}/results` | public after close | 200 | 404, 409 not yet |
| GET | `/elections/{id}/contests/{cid}/audit` | public after close | 200 (both chains) | 404, 409 |
| GET | `/elections/{id}/contests/{cid}/ledger` | public after close | 200 published (shuffled) entries | 404, 409 |

Pagination: `page` is 0-based (default 0) and `size` is 1–500 (default 200). The body stays a plain array.

Actuator: `/actuator/health`, `/health/readiness` and `/health/liveness` are public. **Every other actuator
endpoint, including `/actuator/prometheus`, requires ADMIN.**

## Errors

Every error body is `ApiErrorResponse { timestamp, status, error, message, path, fieldErrors, requestId }`.
`requestId` equals the `X-Request-Id` response header and appears in every log line as `[requestId]`.
`RequestIdFilter` reuses a well-formed incoming `X-Request-Id` (`[A-Za-z0-9._-]{8,64}`) and otherwise generates a UUID.

| Exception | HTTP |
| --- | --- |
| Bean validation (`MethodArgumentNotValidException`) | 400 + fieldErrors |
| Bad path or param types, unreadable JSON, `InvalidBallotTokenException` | 400 |
| `InvalidCredentialsException`, `InvalidVotingCredentialException`, `InvalidBootstrapTokenException`, `InvalidRefreshTokenException` | 401 |
| `AdminBootstrapException`, access denied | 403 |
| `ResourceNotFoundException` | 404 |
| Duplicate, data integrity, registration/voting closed, results unavailable, lifecycle | 409 |
| `EligibilityException` | 422 |
| `LoginThrottledException` (with `Retry-After`), IP rate limit (written by the filter) | 429 |
| **Catch-all**: Spring `ErrorResponse` exceptions keep their 4xx status; Spring Security exceptions keep 401/403; anything else is logged with the request ID and returns a generic 500 | |

## Abuse controls

- **IP rate limiter** (`RateLimitingFilter`, in-memory, fixed window). It applies to POST only: `auth` (login,
  register, refresh, logout), `admin-bootstrap`, `credential-issuance`, `ballot-submission`. It is keyed by
  `getRemoteAddr()`, which `server.forward-headers-strategy=native` resolves from `X-Forwarded-For` when the
  connection comes from a private-network proxy.
- **Per-email login throttle** (`LoginThrottleService`, in-memory): after `max-failures` failures (default 5)
  the email is locked for `lock-seconds` (900). It works the same for unknown emails, and unknown emails also
  pay one BCrypt check, so neither timing nor the throttle reveals which accounts exist.

## CORS

Origins come from `VOTETRUST_CORS_ALLOWED_ORIGINS` (set in the Render dashboard; `sync: false`). Allowed methods:
GET, POST, PUT, PATCH, DELETE, OPTIONS. Exposed headers: `X-Total-Count`, `X-Request-Id`, `Retry-After`.
No credentials or cookies.

## Configuration (`application.properties`)

| Property | Env var | Default |
| --- | --- | --- |
| `server.port` | `SERVER_PORT`, then `PORT` | 8080 |
| `server.forward-headers-strategy` | `SERVER_FORWARD_HEADERS_STRATEGY` | `native` |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` or `_HOST/_PORT/_NAME` | localhost/votetrust |
| `spring.datasource.username/password` | `SPRING_DATASOURCE_USERNAME/PASSWORD` | **required** |
| `votetrust.security.jwt.secret` | `VOTETRUST_JWT_SECRET` | **required, ≥32** |
| `votetrust.security.jwt.access-token-expiration-minutes` | `VOTETRUST_JWT_ACCESS_TOKEN_EXPIRATION_MINUTES` | 15 |
| `votetrust.security.jwt.refresh-token-expiration-hours` | `VOTETRUST_JWT_REFRESH_TOKEN_EXPIRATION_HOURS` | 12 |
| `votetrust.security.login-throttle.{max-failures,lock-seconds}` | `VOTETRUST_LOGIN_MAX_FAILURES/LOCK_SECONDS` | 5, 900 |
| `votetrust.security.identity-hash-pepper` | `VOTETRUST_ID_HASH_PEPPER` | **required, ≥32** |
| `votetrust.security.vote-credential-pepper` | `VOTETRUST_VOTE_CREDENTIAL_PEPPER` | **required, ≥32** (also derives the ballot-key encryption key) |
| `votetrust.security.cors.allowed-origins` | `VOTETRUST_CORS_ALLOWED_ORIGINS` | localhost:5173, :3000 |
| `votetrust.security.rate-limit.*` | `VOTETRUST_RATE_LIMIT_*` | enabled, 60 s window, 100/20/100/100 |
| `votetrust.admin.bootstrap.{enabled,token}` | `VOTETRUST_ADMIN_BOOTSTRAP_*` | false, empty (token ≥32 when enabled) |
| `votetrust.lifecycle.{scheduling-enabled,poll-interval-ms}` | `VOTETRUST_LIFECYCLE_*` | true, 5000 |
| `votetrust.logging.format` | `VOTETRUST_LOG_FORMAT` | `text` (`json` = one JSON object per line; any other value fails startup) |

Property records live in `security/` and `config/LoggingProperties`. They are registered on `SecurityConfig` and
validated at startup. `spring-boot-configuration-processor` generates IDE metadata for them.

Rotating a pepper is destructive: the ID pepper breaks duplicate-ID detection, and the vote-credential pepper
breaks spent-token checks *and* decryption of open contests' ballot keys.
