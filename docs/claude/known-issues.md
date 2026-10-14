# Known issues and gotchas

First review: 2026-10-14 (commit abb0477). Update this file when items are fixed or found.

## Fixed on 2026-10-14

- **CI was red**: `DeploymentArtifactTest` expected the old `spring.datasource.url=${SPRING_DATASOURCE_URL}`. The
  assertion now matches the Render-friendly URL expression and checks that username/password have no defaults.
- **Render CORS blocked the Vercel frontend**: `VOTETRUST_CORS_ALLOWED_ORIGINS` in `render.yaml` is now `sync: false`.
  The value lives in the Render dashboard and Blueprint syncs can't reset it to localhost.
  **Manual step:** the dashboard value must include the Vercel origin.
- **Azure removed**: `infra/azure/`, `.github/workflows/deploy.yml`, `federated-credential.json`, the Azure docs and
  the Azure tests are gone. Render deploys after CI passes (`autoDeployTrigger: checksPass`).
- **District self-selection**: election registration now always uses the voter-profile district. A different
  `votingDistrictId` gets a 422, and the frontend no longer offers a dropdown.
- **Proxy IPs**: `server.forward-headers-strategy=native` lets rate limiting and audit events use the real client IP
  behind Render's proxy. Still to confirm in production (see api.md).
- **Election stuck in DRAFT**: ballot configuration stays open while the election is DRAFT and before
  `registrationEndAt`, so an election that wasn't ready at `registrationStartAt` can be completed and then opens
  automatically.
- **Testcontainers couldn't see Docker 29**: 1.20.1 spoke Docker API 1.32, which Docker Engine 29 rejects
  ("client version 1.32 is too old"), so every integration test was silently skipped. It's now on 1.21.4, and all
  94 tests run and pass.

## Still open: design gaps

1. **Turnout denominator is per election, not per contest.** `registeredVoterCount` and `turnoutPercentage` use every
   ACTIVE registration in the election, including voters who weren't eligible for a given ward or provincial contest.
2. **The public ledger can't be re-hashed independently.** `/ledger` exposes `recordedDate` instead of `castAt`
   (deliberately, for privacy), but `castAt` is in the hash payload. Third parties can recount but can't verify the
   chain; only the server-side `/audit` can. Fixing this needs a new hash version, which is a design decision.
3. **An invalid or expired Bearer token on a public endpoint gives a 401**, because `JwtAuthenticationFilter` rejects it
   before authorization runs. The frontend avoids this by not sending tokens on public calls; keep it that way.
4. **No server-side VOTER role check** on registration or credential issuance. It relies on admins having no voter profile.
5. **No way to change a voter's district** (moving house). The profile district is fixed at onboarding.
6. **A DRAFT election whose registration window has fully passed can only be cancelled.** There's no reschedule endpoint.

## Still open: operational

7. **Render free tier**: the web service sleeps when idle, so the lifecycle scheduler pauses. Transitions catch up on
   the first tick after wake-up. Free Render Postgres instances also expire after a limited period, so check
   Render's current policy.
8. **The frontend isn't in CI.** Run `npm test && npm run lint && npm run build` in `frontend/` manually.
9. **Docker shells**: user `wannacry` was added to the `docker` group on 2026-10-14. Shells started before that
   need `sg docker -c "<cmd>"` (or a fresh login) for Testcontainers to reach the socket.
10. **The rate limiter is in-memory and per-instance**, which is fine on a single Render instance.

## Cosmetic / housekeeping

- `HELP.md` is the Spring Initializr stub and refers to Boot 4.1.0 (the project is on 3.3.4).
- Lombok is declared in `pom.xml` (with annotation-processor config) but nothing uses it.
- Naming drift: the Dockerfile label source is `github.com/humphreymahlangu/voting-api`, the README clone URL is
  `…/VoteTrust.git`, and the actual remote is `…/YourVoice.git`.
- `.gitignore` files list `frontend/.env.development` and `.env.production`, but both are tracked.
