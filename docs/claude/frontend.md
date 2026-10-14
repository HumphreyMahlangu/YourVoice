# Frontend (`frontend/`)

React 19 + TypeScript 6 (strict-ish: `noUnusedLocals/Parameters`, `verbatimModuleSyntax`, `erasableSyntaxOnly`,
so use `import type` and no enums or parameter properties), Vite 8, `react-router` v8 (imported from
`'react-router'`, not `react-router-dom`), Vitest + Testing Library on jsdom, oxlint. Fonts come from `@fontsource-variable`.
There are no state, form, data-fetching or UI libraries.

## Layout

```
src/
  main.tsx            StrictMode > AuthProvider > RouterProvider
  router.tsx          createBrowserRouter; App is the layout (header nav, <Outlet/>, footer)
  App.tsx             nav changes by session.role
  config/environment.ts  API_BASE_URL = VITE_API_BASE_URL (trailing slashes stripped) || http://localhost:8080
  api/client.ts       apiRequest<T>(path, { method, body, authToken, signal, ... }) → throws ApiError
  api/<resource>.ts   one thin function per endpoint (auth, elections, contests, districts, registrations,
                      voting, results, audit, adminManagement, securityAudit)
  auth/               AuthProvider (state), context.ts, useAuth, RequireVoter / RequireAdmin route guards
  types/<resource>.ts TS mirrors of backend DTOs; enums as string-literal unions
  pages/*Page.tsx     one per route
  components/         BrandMark, ElectionMetadata, admin/{AdminContestForm, AdminContestOptionForm},
                      voting/{BallotSelection, BallotReview, LeaveVotingPageWarning}
  utils/              formatters (en-ZA, Africa/Johannesburg, appends "SAST"), getErrorMessage
  styles.css          single global stylesheet with design tokens in :root
```

## Routes

| Path | Page | Guard |
| --- | --- | --- |
| `/` | HomePage | — |
| `/elections`, `/elections/:electionId` | ElectionsPage, ElectionDetailsPage | — |
| `/elections/:eid/contests/:cid/{results,audit,ledger}` | ContestResultsPage, ContestAuditPage, ContestLedgerPage | — |
| `/login`, `/register` | LoginPage, RegisterPage (redirect away if already signed in) | — |
| `/dashboard` | VoterDashboardPage (calls `/auth/me` + `/me/registrations`) | RequireVoter |
| `/elections/:eid/register` | ElectionRegistrationPage (confirm-only, sends `{}`; the API uses the profile district) | RequireVoter |
| `/elections/:eid/contests/:cid/vote` | VotePage | RequireVoter |
| `/admin` | AdminDashboardPage (lists elections + districts) | RequireAdmin |
| `/admin/security-events` | AdminSecurityEventsPage (limit selector + refresh) | RequireAdmin |
| `/admin/elections/new` | AdminCreateElectionPage | RequireAdmin |
| `/admin/elections/:eid/configure` | AdminElectionBuilderPage (add contests/options while DRAFT and before `registrationEndAt`; shows an "overdue" notice after regStart and a cancel hint once locked) | RequireAdmin |
| `/admin/elections/:eid/operations` | AdminElectionOperationsPage (lifecycle history + emergency cancel) | RequireAdmin |
| `/admin/voting-districts/new` | AdminCreateVotingDistrictPage | RequireAdmin |
| `*` | NotFoundPage; `errorElement` = RouteErrorPage | |

Guards: no session → `/login`. Wrong role: RequireAdmin sends a voter to `/dashboard`; RequireVoter sends an admin to `/`.
`vercel.json` rewrites every path to `index.html` so deep links work.

## Auth session model

- `AuthSession` = the backend `AuthResponse` (`accessToken, tokenType, expiresAt, userId, email, role`).
- It's held **only in React state**. There's no localStorage or cookie, so **a page refresh signs the user out**.
  This is deliberate: the token isn't persisted, and the vote credential lives only in a `useRef`. Don't "fix" it by
  persisting tokens without asking.
- `AuthProvider` sets a timer that clears the session at `expiresAt` (15 minutes by default). There's no refresh.
- Pages handle a 401 from an authenticated call with `logout()`. The guard then redirects to `/login`.

## Patterns to copy

**Data loading in effects.** Every page uses this shape:

```ts
useEffect(() => {
  const abortController = new AbortController()
  async function load() {
    setError(null); setIsLoading(true)
    try {
      const data = await getX(id, abortController.signal)
      if (!abortController.signal.aborted) setData(data)
    } catch (e) {
      if (!abortController.signal.aborted) setError(e)
    } finally {
      if (!abortController.signal.aborted) setIsLoading(false)
    }
  }
  void load()
  return () => abortController.abort()
}, [id])
```

Errors are stored as `unknown` and rendered with `getErrorMessage(error, 'fallback')` inside `<p role="alert">`.
Loading text uses `<p role="status">`.

**Forms.** Controlled inputs with `useState`. On submit, reset `error` and `fieldErrors`. On an `ApiError`, copy
`error.fieldErrors` into state and show each next to its input with `aria-invalid` and `aria-describedby`.
Client-side checks mirror the server's (e.g. the election window order in AdminCreateElectionPage).
`datetime-local` values go to the API as `new Date(v).toISOString()`.

**API modules.** IDs are passed through `encodeURIComponent`. Authenticated calls take `authToken` as a parameter
(read from `useAuth().session`). Nothing calls `fetch` directly.

**Accessibility is a first-class concern.** Each page is a `<section className="page …" aria-labelledby>`,
headings carry ids, and there's a skip link. Keep it that way.

## VotePage state machine (the most delicate UI)

Phases: `selecting → reviewing → submitting → accepted | uncertain | failed` (a retry goes back to `reviewing`).
The credential is requested **only when the user confirms**, then the ballot is cast right away.

- The raw credential lives in `credentialRef` (a ref, never state or storage). While it's held, `useBlocker` and
  `beforeunload` warn before the user navigates away.
- Credential step: 401 → logout. 409 or a network error → `uncertain`, because the credential may or may not have
  been issued. Any other ApiError → `failed`.
- Ballot step: 409 containing "already been used" → `uncertain`. 429, ≥500 or a network error → keep the credential
  and allow a retry. Anything else → `failed`.
- On success it shows no selection, ID or receipt (this mirrors the backend anonymity invariant).
- The 409 check matches the backend message text `"Voting credential has already been used"`, so keep the two in sync.

## Styling

There's one global `styles.css` (~1,500 lines). Tokens are in `:root`: `--color-*`, `--font-display` (Plus Jakarta
Sans) and `--font-body` (Inter), a type scale, `--radius-*`, `--shadow-*`, `--content-width: 1200px`. Breakpoints
are at 1040px and 760px, and there's a `prefers-reduced-motion` block. Class names are BEM-ish (`app-header__inner`,
`nav-link--active`, `button button--primary`). Page wrappers have modifier classes like `auth-page`,
`dashboard-page`, `admin-page`, `workflow-page`, `verification-page`, `directory-page`. Many inner page elements are
bare semantic HTML styled by element selectors. There's no dark mode.

## Env and tests

- `VITE_API_BASE_URL` lives in `.env.development` and `.env.production` (both currently point at the Render API,
  `https://votetrust-api.onrender.com`). `.env.example` points to localhost. To use a local backend in dev, create
  `.env.development.local` (git-ignored by `*.local`).
- Tests: `api/client.test.ts` (headers, errors), `auth/RequireAdmin.test.tsx` (renders guards with a hand-built
  `AuthContext.Provider` value, which is the pattern for testing anything that uses `useAuth`), and
  `utils/formatters.test.ts`. Setup: `src/test/setup.ts` (jest-dom matchers + cleanup).
- Status on 2026-10-14: `npm test` 10/10 pass, `npm run lint` clean, `npm run build` OK.
