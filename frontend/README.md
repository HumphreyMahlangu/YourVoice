# YourVoice Frontend

The React single-page application for [YourVoice](../README.md). Voters register and vote here, administrators run elections, and anyone can verify the results of a closed contest.

Built with React 19, TypeScript, Vite, and React Router. It has no UI or state-management libraries: styling is a single global stylesheet, and the API layer is a small `fetch` wrapper.

## Commands

```bash
npm install          # install dependencies
npm run dev          # development server on http://localhost:5173
npm test             # unit tests (Vitest + Testing Library)
npm run test:watch   # tests in watch mode
npm run lint         # oxlint
npm run build        # type-check and build to dist/
npm run preview      # serve the production build locally
```

## Configuration

The API address comes from `VITE_API_BASE_URL` and is fixed at build time.

| File | Used by | Points at |
| --- | --- | --- |
| `.env.development` | `npm run dev` | the deployed Render API |
| `.env.production` | `npm run build` (and Vercel) | the deployed Render API |
| `.env.development.local` (git-ignored) | `npm run dev`, overrides the above | e.g. `http://localhost:8080` for a local API |

The API must list the frontend's origin in `YOURVOICE_CORS_ALLOWED_ORIGINS`.

## How it is organised

| Path | Contents |
| --- | --- |
| `src/api/` | One module per API resource, built on `apiRequest` in `client.ts` |
| `src/auth/` | Session state, token refresh, and the voter and admin route guards |
| `src/pages/` | One component per route (see `router.tsx`) |
| `src/components/` | Shared, admin, and voting components |
| `src/utils/` | Formatting, `blindSignature.ts` (voter side of the blind-signature protocol), `ledgerVerification.ts` (in-browser ledger check) |
| `src/types/` | TypeScript mirrors of the API's request and response shapes |

## Security notes

* The access token is kept in memory only. The refresh token is kept in `sessionStorage`, so a reload keeps you signed in and closing the tab ends the session.
* While voting, the ballot token and signature live only in memory. The page warns before you leave while you hold one.
* Ballot tokens are blinded in the browser before they are sent, so the API cannot link a ballot to the voter who requested it.

## Deployment

Vercel builds this directory with `npm run build`. `vercel.json` rewrites every path to `index.html` so client-side routes work on reload. See [`../DEPLOYMENT.md`](../DEPLOYMENT.md).
