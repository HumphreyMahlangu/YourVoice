# Frontend (`frontend/`)

React 19 + TypeScript 6 (`noUnusedLocals/Parameters`, `verbatimModuleSyntax`, `erasableSyntaxOnly`, so use
`import type` and no enums or parameter properties; `Uint8Array`s passed to WebCrypto must be typed
`Uint8Array<ArrayBuffer>`). Vite 8, `react-router` v8 (imported from `'react-router'`), Vitest + Testing Library
on jsdom, oxlint. There are no state, form, data or UI libraries. CI runs lint, tests and build (`frontend` job).

## Layout

```
src/
  main.tsx              StrictMode > AuthProvider > RouterProvider
  router.tsx            createBrowserRouter; App is the layout
  config/environment.ts API_BASE_URL from VITE_API_BASE_URL
  api/client.ts         apiRequest<T>(path, { method, body, authToken, signal }) → throws ApiError (with requestId)
  api/<resource>.ts     auth, elections, contests, districts, registrations, voting (ballot key, credential, ballot),
                        results, audit, adminManagement (create/update/delete), adminAccounts, securityAudit
  auth/                 AuthProvider (session + refresh), context.ts, useAuth, RequireVoter / RequireAdmin
  types/                TS mirrors of backend DTOs (string-literal unions for enums)
  pages/                one per route
  components/           ElectionMetadata, admin/{AdminContestForm, AdminContestOptionForm, AdminRescheduleElectionForm},
                        voting/{BallotSelection, BallotReview, LeaveVotingPageWarning}
  utils/                formatters, getErrorMessage, dateTimeInput (datetime-local ⇄ UTC),
                        blindSignature (voter side of the RSA blind signature), ledgerVerification (in-browser chain check)
  styles.css            single global stylesheet, tokens in :root
```

## Routes

| Path | Page | Guard |
| --- | --- | --- |
| `/`, `/elections`, `/elections/:eid` | Home, Elections, ElectionDetails | — |
| `/elections/:eid/contests/:cid/{results,audit,ledger}` | results; audit (both chains); ledger (verified in the browser) | — |
| `/login`, `/register` | | — |
| `/dashboard` | VoterDashboardPage | RequireVoter |
| `/elections/:eid/register` | ElectionRegistrationPage (confirm only, sends `{}`) | RequireVoter |
| `/elections/:eid/contests/:cid/vote` | VotePage | RequireVoter |
| `/admin` | dashboard with links to every admin tool | RequireAdmin |
| `/admin/elections/new` | create election | RequireAdmin |
| `/admin/elections/:eid/configure` | builder: add or remove contests and options while DRAFT and before `registrationEndAt` | RequireAdmin |
| `/admin/elections/:eid/operations` | lifecycle history, reschedule form (DRAFT), emergency cancel | RequireAdmin |
| `/admin/voting-districts`, `/admin/voting-districts/new` | list, edit, delete / create | RequireAdmin |
| `/admin/accounts` | list and create administrators | RequireAdmin |
| `/admin/security-events` | audit events | RequireAdmin |

While `isInitializing` is true the guards show "Restoring your session..."; otherwise no session sends the user
to `/login`, and the wrong role sends them to `/dashboard` or `/`.

## Auth session

- `AuthSession` = backend `AuthResponse`, including `refreshToken` and `refreshTokenExpiresAt`.
- The access token lives in React state only. The **refresh token is kept in `sessionStorage`**
  (`yourvoice.refreshToken`): tab-scoped, it survives reloads and is cleared when the tab closes. Cookies aren't
  used because the frontend (Vercel) and API (Render) are different sites. The trade-off is that XSS could read it.
- On load, a stored refresh token is exchanged at `/auth/refresh`. While a session exists, it's refreshed 60 s
  before the access token expires. A failed refresh signs the user out.
- `logout()` revokes the session on the server (best effort) and clears storage. Pages call `logout()` on any
  401 from an authenticated request. `login`, `register` and `logout` are memoized with `useCallback`.

## Patterns to copy

- **Data loading:** `useEffect` + `AbortController`, ignore results after abort, keep errors as `unknown`, render
  them with `getErrorMessage(error, 'fallback')` in `<p role="alert">`, loading text in `<p role="status">`.
- **Forms:** controlled inputs, then reset errors on submit. On `ApiError`, copy `fieldErrors` next to the inputs
  with `aria-invalid` and `aria-describedby`. `datetime-local` values convert through `utils/dateTimeInput`.
- **Destructive actions:** inline two-step confirmation (button → "Confirm …" / "Keep"), never `window.confirm`.
- **API modules:** `encodeURIComponent` every ID, take `authToken` as a parameter, never call `fetch` directly.
- **Accessibility:** each page is a `<section className="page …" aria-labelledby>`, and headings have ids.

## VotePage (blind-signature flow)

Phases: `selecting → reviewing → submitting → accepted | uncertain | failed`.
On confirm: `getBallotKey` → `blindBallotToken(key)` → `issueVotingCredential(blindedToken)` →
`unblindSignature` (which checks the result against the key) → `castBallot({ballotToken, signature})`.

- `credentialRef` (a ref, never state or storage) holds the blinded ballot plus, once known, the signature. While
  it's held, `useBlocker` and `beforeunload` warn before leaving the page.
- Issuance failures from the network, 429 or ≥500 keep the blinded ballot and allow a retry. Retrying with the
  same blinded token is idempotent on the server. A 409 "already been issued" (a different token, e.g. from
  another tab) and a ballot-stage 409 "already been used" both go to `uncertain`. The page can't look up
  whether a ballot was counted, by design.
- The accepted screen shows the contest key fingerprint, so voters can compare keys.
- Message matching depends on backend texts "already been issued" and "already been used", so keep them in sync.

## Env and tests

- `VITE_API_BASE_URL` in `.env.development` and `.env.production` (Render API). For a local backend, create
  `.env.development.local` (git-ignored).
- Tests: `api/client.test.ts`, `auth/RequireAdmin.test.tsx` (hand-built `AuthContext` value: include
  `isInitializing`), `auth/AuthProvider.test.tsx` (session restore, failed refresh, logout revocation, using
  `vi.mock('../api/auth')`), `utils/blindSignature.test.ts` (shared hash vector with Java plus a WebCrypto-key
  round-trip), `utils/ledgerVerification.test.ts` (shared public-hash vector), `utils/dateTimeInput.test.ts`,
  `utils/formatters.test.ts`.
