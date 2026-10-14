# Known issues and gotchas

First review: 2026-10-14 (commit abb0477). The backend hardening on `feature/backend-hardening` addressed most
findings. Update this file whenever items are fixed or found.

## Fixed on branch `feature/backend-hardening`

- CI failure, Render CORS, Azure removal, district self-selection, proxy client IPs, elections stuck in DRAFT,
  Testcontainers vs Docker 29 (the first batch of fixes).
- **Public ledger deanonymization by order:** the public ledger is now a shuffled re-chain with fresh nonces;
  internal entries store only the recording day (hash v2).
- **The public ledger couldn't be verified by third parties:** the published chain's hash inputs are all public,
  and the ledger page verifies it in the browser.
- **The server could link credentials to ballots:** RSA blind signatures (see domain.md).
- **Lost credential response was a dead end:** issuance is idempotent for the same blinded token, so the vote page retries.
- **Concurrent first ballots lost a voter's credential:** ledger state is created with `ON CONFLICT DO NOTHING`.
  Concurrency tests found and fixed a stale-entity bug in publication.
- **Turnout denominator** is per contest.
- **Login timing user enumeration, duplicate-ID message oracle, no per-account throttling, no refresh/logout,
  no catch-all error handler, no request IDs, no metrics, scheduler not cluster-safe:** all fixed (see api.md).
- **No admin corrections, a single admin, no pagination, N+1 contest listing, unused Lombok, stale HELP.md,
  frontend missing from CI, Logback startup noise:** all fixed.

## Still open: by design or residual risk

1. **The database operator is still trusted for cast order.** The internal ledger keeps cast order (as index) for
   tamper evidence, and the server sees request arrival times. Anyone with DB access plus sign-in timing can
   still correlate. Removing that needs a mix-net or verifiable shuffle, which is out of scope.
2. **Key-tagging relies on voters comparing fingerprints.** One key per contest is enforced server-side, but a
   malicious server could serve different keys; only comparing `keyFingerprint` across voters detects it.
3. **No admin re-issue of credentials.** With blind signatures the server can't tell whether an earlier
   credential was spent, so a re-issue could allow a second ballot. A voter who loses the browser tab after
   issuance (the blinded state lives only in memory) can't vote in that contest.
4. **Uniqueness checks are an oracle.** The generic duplicate message hides *which* of email or ID collided, but
   with a fresh email a collision still means "this ID is registered". Rate limits and audit events limit
   probing; email verification would be the real fix.
5. **The ID hash is only as strong as the pepper.** The SA ID space is small, and `date_of_birth` is stored next
   to the hash (it's needed for the voting-age check). A leaked pepper makes IDs recoverable.
6. **Access tokens can't be revoked.** Logout revokes refresh tokens; an access token stays valid for up to 15 minutes.
7. **The refresh token is in `sessionStorage`**, so XSS could read it. Cookies are unusable because Vercel and
   Render are different sites (a shared parent domain would allow httpOnly cookies).
8. **Rate limiting and login throttling are in-memory and per instance.** That's fine on one Render instance;
   move them to Redis or Postgres if you scale out.
9. **Pepper rotation is destructive**, including for open contests' ballot keys (see api.md).
10. **An invalid or expired Bearer token on a public endpoint gives a 401.** The frontend avoids sending tokens on public calls.
11. **There's no server-side VOTER role check** on registration and issuance; it relies on admins having no profile.

## Operational

- **Render free tier** sleeps when idle and pauses the scheduler. Set `RENDER_KEEPALIVE_URL` to enable
  `keep-alive.yml`, or use a paid instance. Free Postgres instances also expire after a limited period.
- **Docker shells:** user `wannacry` joined the `docker` group on 2026-10-14. Older shells need `sg docker -c …`
  or `newgrp docker`.
- **The developer's local DB was migrated to V14** on 2026-10-14 by running this branch's image. Code from
  `main` (V10) refuses to start against it, because Flyway sees unknown applied migrations. Either keep using this
  branch, or reset the local DB with `docker compose down -v` (this deletes local data, including the admin).

## Cosmetic

- Naming drift: the Dockerfile label source is `github.com/humphreymahlangu/voting-api`, the README clone URL is
  `…/VoteTrust.git`, and the actual remote is `…/YourVoice.git`.
- `.gitignore` lists `frontend/.env.development` and `.env.production`, but both are tracked.
