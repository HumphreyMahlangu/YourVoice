# YourVoice
**A Secure Digital Voting Platform**

[![CI](https://github.com/HumphreyMahlangu/YourVoice/actions/workflows/ci.yml/badge.svg)](https://github.com/HumphreyMahlangu/YourVoice/actions/workflows/ci.yml)

A full-stack web application that explores how modern software engineering, cryptography, and secure system design can improve transparency, accessibility, and trust in digital voting: a React single-page application for voters, administrators, and public observers, backed by a Spring Boot REST API and PostgreSQL.

## Table of Contents
* [Overview](#overview)
* [Why YourVoice?](#why-yourvoice)
* [The Problem](#the-problem)
* [Project Vision and Goals](#project-vision-and-goals)
* [Core Principles](#core-principles)
* [What You Can Do](#what-you-can-do)
* [Key Features](#key-features)
* [Technology Stack](#technology-stack)
* [Architecture](#architecture)
* [Repository Structure](#repository-structure)
* [Getting Started](#getting-started)
* [Testing](#testing)
* [Deployment](#deployment)
* [REST API](#rest-api)
* [Project Status](#project-status)
* [Roadmap](#roadmap)
* [About the Developer](#about-the-developer)
* [Disclaimer](#disclaimer)
* [License](#license)

## Overview
YourVoice is a full-stack software engineering project that explores how a secure digital voting system can be designed end to end, from the browser to the database.

The **frontend** is a React and TypeScript single-page application where voters register, vote, and verify results, administrators run elections, and anyone can audit a closed contest. Security-critical work happens in the browser too: ballot tokens are blinded with the contest's public key before they leave the voter's device, and the public ledger is re-verified locally.

The **backend** is a Spring Boot REST API that handles authentication, eligibility, the automatic election lifecycle, blind signing, ballot recording in tamper-evident hash chains, tallying, and auditing, all on PostgreSQL.

Rather than attempting to replace existing election systems, YourVoice is a technical exploration of the challenges involved in designing a secure digital voting platform while following modern software engineering best practices.

## Why YourVoice?
Digital technology has transformed how people bank, communicate, study, shop, and access government services. However, voting remains a largely physical process in many parts of the world.

While in-person voting plays an important role in maintaining election integrity, practical barriers such as long queues, travel requirements, accessibility challenges, and time constraints may discourage participation for some eligible voters.

YourVoice was created to explore an important engineering question: how can modern software technologies improve accessibility to voting while preserving security, privacy, transparency, and public trust?

This project does not claim to solve every challenge associated with online voting. Instead, it investigates how secure software engineering principles can contribute to future digital voting systems.

## The Problem
Designing a secure digital voting system is significantly more complex than building a standard web application. A trustworthy voting platform must address challenges such as:
* Verifying voter identity.
* Preventing duplicate voting.
* Protecting ballot secrecy, even from the operators of the system.
* Preventing unauthorized modification of votes.
* Providing transparent and independently verifiable election records.
* Preserving voter privacy.
* Maintaining confidence in election results.

## Project Vision and Goals
The vision of YourVoice is a secure, maintainable, full-stack platform that demonstrates how modern technologies can be combined into a transparent and trustworthy digital voting system, with clean architecture, security-first development, and real-world engineering practices across the whole stack.

* Build an accessible, responsive web interface for voters, administrators, and observers.
* Build a secure RESTful API with robust authentication and authorization.
* Ensure every eligible voter can cast only one vote.
* Protect ballot secrecy cryptographically, so ballots cannot be linked to voters.
* Maintain tamper-evident records that anyone can verify.
* Apply secure software engineering principles throughout the system.
* Produce a professional portfolio project that reflects real-world engineering practices.

## Core Principles
* **Security**: Protect voter identities, ballots, and election data against unauthorized access and tampering.
* **Privacy**: Ensure that individual votes remain confidential while maintaining election integrity.
* **Integrity**: Guarantee that votes cannot be modified, duplicated, or removed once accepted by the system.
* **Transparency**: Provide verifiable records that improve accountability without exposing sensitive information.
* **Accessibility**: Explore how digital platforms may reduce practical barriers that discourage participation.

---

## What You Can Do

### As a voter
* Create an account once, with your South African ID number and voting district. The ID number is validated and stored only as a protected hash.
* Register for an election while its registration window is open.
* Vote in the contests you are eligible for. Your browser blinds a random ballot token, the server signs it after checking eligibility, and your ballot is cast without your identity.
* Stay signed in across page reloads, and sign out to revoke the session.

### As an administrator
* Create voting districts, elections, contests, and ballot options (party, independent candidate, blank, spoilt).
* Edit or remove draft ballot content, and rename or reschedule a draft election.
* Watch each election's lifecycle history, and cancel an election in an emergency.
* Review security audit events and add other administrators.

### As a public observer
* Browse elections and contests.
* After an election closes, view the final results, the server's integrity audit, and the published ledger, which your browser re-verifies hash by hash.

## Key Features

### Unlinkable Ballot Credentials (Blind Signatures)
* Each contest has its own RSA key. The voter's browser generates a random ballot token, blinds it, and asks the server to sign the blinded value after the eligibility checks.
* The browser removes the blinding and casts the ballot with the token and signature, without the voter's login token. The server can verify the signature but cannot link the ballot to the voter it signed for.
* Each voter can obtain one signature per contest. Repeating a request with the same blinded token returns the same signature, so a lost response can be retried safely.
* Private keys are encrypted at rest (AES-256-GCM), and voters can compare the contest key fingerprint shown after voting.

### One Person, One Vote
* Database uniqueness constraints prevent duplicate registrations, voting rights, spent ballot tokens, and ledger positions.
* Pessimistic locks and race-free inserts serialize voting rights, token spending, and ledger appends. Dedicated concurrency tests release many simultaneous requests against PostgreSQL and require exactly one success.

### Ballot Privacy
* Ballot ledgers contain no account, voter profile, registration, or voting-right reference.
* The public ledger is published after voting closes with ballots shuffled into a random order, so ledger positions say nothing about when, or by whom, a ballot was cast.
* The database stores only the day each ballot was recorded, never the exact time. Ballot responses contain no receipt, and no metric is broken down by option.

### Tamper-Evident, Publicly Verifiable Ledgers
* Ballots are chained with SHA-256 in an internal ledger (cast order) and, after closing, in a published ledger (shuffled order) whose hash inputs are all public.
* Anyone can recompute the published chain, and the ledger page does it in the browser. The server audit also checks the internal chain and that both ledgers contain the same ballots.
* The hash chains are tamper-evident, not a blockchain. Stronger real-world assurance would require restricted database administration, external hash anchoring, signed exports, and independent oversight.

### Authentication and Account Security
* Short-lived JWT access tokens with rotating refresh tokens. Refresh tokens are stored hashed, and reusing a rotated token revokes the whole session family.
* Passwords hashed with BCrypt (cost 12). Unknown emails still pay for one BCrypt check, so timing does not reveal which accounts exist.
* Per-IP rate limits plus a temporary lock on an email address after repeated failed sign-ins, applied equally to unknown emails.
* Role-based access for voters and administrators. The first administrator comes from a disabled-by-default, token-protected bootstrap; further administrators are added from the app and audited.

### Elections, Eligibility, and Results
* Election stages (registration open and closed, voting open, completed) advance automatically on schedule. A database lock keeps the scheduler safe across multiple instances.
* National, provincial, municipal proportional-representation, and ward contests, with eligibility derived from the voting district captured at onboarding.
* Results become available only after voting has ended. Turnout is computed against the voters eligible for each contest, and blank and spoilt ballots are counted separately from valid votes.

### Frontend Experience
* Accessible pages built on semantic HTML: labelled forms with inline field errors, skip links, live status and alert regions, and keyboard-friendly two-step confirmations for destructive actions.
* The voting flow guards the in-memory ballot credential: it warns before you leave the page, retries safely after network errors, and never claims an outcome it cannot confirm.
* Cryptography runs in the browser with the Web Crypto API and native `BigInt` (blinding, unblinding, and ledger verification), with test vectors shared with the backend.

### Operations
* Every response carries a request ID that also appears in error bodies and logs.
* Prometheus metrics (administrators only), optional JSON logs, readiness and liveness probes, Flyway migrations, a hardened Docker image, Postman collections, and CI for backend and frontend.

## Technology Stack

| Layer | Technology |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, React Router, Web Crypto API |
| Frontend quality | Vitest, Testing Library, oxlint |
| Backend | Java 21, Spring Boot 3, Spring MVC, Jakarta Bean Validation |
| Security | Spring Security, stateless JWT (JJWT), BCrypt, RSA blind signatures, HMAC-SHA-256, AES-GCM |
| Persistence | PostgreSQL 16, Spring Data JPA and Hibernate, Flyway |
| API documentation | Springdoc OpenAPI and Swagger UI |
| Backend quality | JUnit 5, Mockito, Spring Security Test, Testcontainers |
| Operations | Spring Boot Actuator, Micrometer and Prometheus, ShedLock, Docker, Docker Compose |
| Hosting and CI | Heroku (bundled frontend, API and PostgreSQL), GitHub Actions |

## Architecture

```text
                Browser (React SPA, bundled on Heroku)
                ├─ pages for voters, administrators, observers
                ├─ blinds and unblinds ballot tokens (Web Crypto + BigInt)
                └─ re-verifies the published ledger
                                │  HTTPS + JSON, JWT bearer tokens
                                ▼
                Spring Boot REST API (Java buildpack, hosted on Heroku)
                ├─ security filters: request IDs, rate limiting, JWT
                ├─ controllers with validated DTOs
                ├─ transactional services: auth, eligibility, lifecycle scheduler,
                │  blind signing, hash-chain ledgers, tallying, audit
                └─ Spring Data JPA repositories
                                │
                                ▼
                PostgreSQL (Flyway-managed schema)
```

The backend follows a layered design (controllers → services → repositories). The frontend is a client-rendered SPA that talks to the API over CORS, keeps the access token in memory and the refresh token in tab-scoped `sessionStorage`, and never persists ballot credentials.

## Repository Structure

```text
.
├── frontend/                 React + TypeScript SPA (Vite)
│   ├── src/api/              one module per API resource
│   ├── src/auth/             session handling and route guards
│   ├── src/pages/            one component per route
│   ├── src/components/       shared, admin, and voting components
│   └── src/utils/            formatting, blind signatures, ledger verification
├── src/main/java/…/yourvoice Spring Boot API (controller, service, repository,
│                             entity, dto, security, config, observability)
├── src/main/resources/       application.properties, Flyway migrations, logging
├── src/test/                 unit, integration, concurrency, and artifact tests
├── postman/                  verified end-to-end Postman collection
├── compose.yaml, Dockerfile  local stack and production image
├── system.properties         Heroku Java 21 runtime selection
├── render.yaml               historical Render Blueprint (not the active deployment)
└── .github/workflows/        backend and frontend CI
```

## Getting Started

### Prerequisites
* Git
* Docker with Docker Compose (for the API and database, and for the backend integration tests)
* Node.js 22.12 or newer and npm (for the frontend; CI uses Node 24)
* Java 21 (only to run the API or tests outside Docker)

### 1. Clone the repository

```bash
git clone https://github.com/HumphreyMahlangu/YourVoice.git
cd YourVoice
```

### 2. Start the API and database

```bash
cp .env.example .env        # local-only values; never reuse them anywhere else
docker compose --env-file .env up --build -d
curl http://localhost:8080/actuator/health/readiness   # {"status":"UP"} when ready
```

The API is now at `http://localhost:8080`, with Swagger UI at `http://localhost:8080/swagger-ui.html`.

### 3. Create the first administrator

`.env.example` enables the one-time bootstrap for local use:

```bash
curl -X POST http://localhost:8080/api/v1/admin/bootstrap \
  -H 'Content-Type: application/json' \
  -H "X-YourVoice-Bootstrap-Token: $(grep ^YOURVOICE_ADMIN_BOOTSTRAP_TOKEN .env | cut -d= -f2)" \
  -d '{"email":"admin@example.com","password":"VeryStrongPassword1"}'
```

Then set `YOURVOICE_ADMIN_BOOTSTRAP_ENABLED=false` in `.env`. Further administrators can be added from the app.

### 4. Start the frontend

```bash
cd frontend
npm install
echo 'VITE_API_BASE_URL=http://localhost:8080' > .env.development.local   # use the local API
npm run dev
```

Open `http://localhost:5173`. Sign in as the administrator to create a district and an election, then create a voter account to register and vote. Election stages advance automatically at the times you set.

To stop everything, press `Ctrl+C` in the frontend terminal and run `docker compose down` (add `-v` to delete the local database).

### Running the API without Docker

Provide a PostgreSQL 16 database and the required environment variables, then use the Maven wrapper (`mvnw.cmd` on Windows):

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/yourvoice
export SPRING_DATASOURCE_USERNAME=yourvoice
export SPRING_DATASOURCE_PASSWORD=<local database password>
export YOURVOICE_JWT_SECRET=<random, at least 32 characters>
export YOURVOICE_ID_HASH_PEPPER=<independent random value, at least 32 characters>
export YOURVOICE_VOTE_CREDENTIAL_PEPPER=<another independent random value, at least 32 characters>
./mvnw spring-boot:run
```

Optional settings are documented in `src/main/resources/application.properties`.

## Testing

```bash
./mvnw clean verify                          # backend: unit, integration, concurrency, artifact tests
cd frontend && npm run lint && npm test && npm run build   # frontend
```

* Backend integration tests run against PostgreSQL in Testcontainers and are skipped, not failed, when Docker is unavailable.
* The [verified Postman collection](postman/YourVoice.verified.postman_collection.json) and [local environment](postman/YourVoice.verified-local.postman_environment.json) run a complete election end to end, including blinding in the collection scripts.
* GitHub Actions runs both the backend and frontend checks on every push and pull request to `main`.

## Deployment

* **API and database: Heroku.** The existing app is deployed on Heroku. Automatic GitHub deploys from `main` are enabled, with **Wait for GitHub checks to pass before deploy** checked (dashboard verified 2026-10-29). A subsequent automatic release has not yet been observed.
* **Frontend and API:** Heroku serves both at `https://yourvoice-08c4a385f94e.herokuapp.com` (confirmed 2026-10-29), with `BUILD_FRONTEND=true` and same-origin API requests. Vercel is not the intended frontend; leave its settings and deployments unchanged.

### Local Compose Smoke Test

Copy `.env.example` to `.env`, set the required secrets, then run `docker compose --env-file .env config` and `docker compose --env-file .env up --build`. Check `/actuator/health/readiness` and `/swagger-ui.html` on port 8080.

### Heroku Backend Deployment

Keep the existing Heroku app and Postgres database. The Java buildpack uses root `pom.xml`; `system.properties` pins `java.runtime.version=21`. Heroku provides `PORT` and Postgres supplies `DATABASE_URL`, from which the Java buildpack derives Spring datasource values. Do not set old Render connection values or recreate the database.

Keep three independent secrets of at least 32 characters (`YOURVOICE_JWT_SECRET`, `YOURVOICE_ID_HASH_PEPPER`, and `YOURVOICE_VOTE_CREDENTIAL_PEPPER`) in Heroku Config Vars. Preserve both peppers after registration. Keep `BUILD_FRONTEND=true` for the confirmed bundled frontend. Leave `VITE_API_BASE_URL` unset for Heroku builds so the browser uses the same origin; never point it at the retired Render API. Preserve existing `YOURVOICE_CORS_ALLOWED_ORIGINS` configuration; same-origin requests need no new CORS origin.

After deployment, check `/actuator/health/readiness`, the homepage and a direct `/elections` refresh. Bootstrap is only for initial admin creation: temporarily enable `YOURVOICE_ADMIN_BOOTSTRAP_ENABLED` and set a strong `YOURVOICE_ADMIN_BOOTSTRAP_TOKEN`, call `POST /api/v1/admin/bootstrap`, then disable bootstrap and remove the token.

### CI Deployment Gates

CI runs backend tests with PostgreSQL, validates Compose, builds the container, and runs frontend lint, tests, and build on `main`. In the existing Heroku app's **Deploy → GitHub** settings, connect `HumphreyMahlangu/YourVoice`, confirm the production branch (`main` if using the current CI triggers), select **Wait for CI to pass before deploy**, and click **Enable Automatic Deploys**. Verify the dashboard confirms both the branch and CI gating. A repository edit alone does not enable this setting.

After the next authorised push, both **Maven Test** and **Frontend Checks** must pass for the same commit before Heroku deploys it. Verify the release in Heroku Activity and repeat the readiness/UI checks. No separate GitHub Actions deployment job or Heroku API key is needed for the existing GitHub integration. See [Heroku's GitHub integration documentation](https://devcenter.heroku.com/articles/github-integration).

`render.yaml` is retained for historical compatibility, not used for current deployment. The legacy Render keep-alive workflow has no schedule. Do not provision Render resources or use its old API URL for the active frontend.

## REST API

Interactive documentation is served at `/swagger-ui.html`, and the OpenAPI document at `/api-docs`.

| Method | Endpoint | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Public | Create a voter account and protected voter profile. |
| `POST` | `/api/v1/auth/login` | Public | Return an access token and a refresh token. |
| `POST` | `/api/v1/auth/refresh` | Refresh token | Rotate the refresh token and issue a new access token. |
| `POST` | `/api/v1/auth/logout` | Refresh token | Revoke the session. |
| `GET` | `/api/v1/auth/me` | Authenticated | Return the current account. |
| `POST` | `/api/v1/admin/bootstrap` | Bootstrap token | Create the first administrator during a temporary bootstrap window. |
| `GET`, `POST` | `/api/v1/admin/accounts` | Administrator | List or add administrators. |
| `POST`, `PUT`, `DELETE` | `/api/v1/admin/voting-districts[/{id}]` | Administrator | Manage voting districts. |
| `POST` | `/api/v1/admin/elections` | Administrator | Create an election. |
| `PUT` | `/api/v1/admin/elections/{electionId}` | Administrator | Rename or reschedule a draft election. |
| `PATCH` | `/api/v1/admin/elections/{electionId}/status` | Administrator | Emergency-cancel an election. |
| `GET` | `/api/v1/admin/elections/{electionId}/lifecycle-events` | Administrator | Read the election's lifecycle history. |
| `POST`, `PUT`, `DELETE` | `/api/v1/admin/elections/{electionId}/contests[/{contestId}]` | Administrator | Manage contests while the ballot is a draft. |
| `POST`, `PUT`, `DELETE` | `/api/v1/admin/elections/{electionId}/contests/{contestId}/options[/{optionId}]` | Administrator | Manage ballot options while the ballot is a draft. |
| `GET` | `/api/v1/admin/security-audit-events` | Administrator | Review recent security audit events. |
| `GET` | `/api/v1/voting-districts` | Public | List voting districts. |
| `GET` | `/api/v1/elections`, `/api/v1/elections/{electionId}` | Public | List or retrieve elections. |
| `POST` | `/api/v1/elections/{electionId}/registrations` | Voter | Register for an election in the voter's onboarding district. |
| `GET` | `/api/v1/me/registrations` | Voter | List the voter's registrations. |
| `GET` | `/api/v1/elections/{electionId}/contests` | Public | List contests and ballot options. |
| `GET` | `/api/v1/elections/{electionId}/contests/{contestId}/ballot-key` | Public once voting opens | Return the contest's public key for blinding. |
| `POST` | `/api/v1/elections/{electionId}/contests/{contestId}/credentials` | Voter | Blind-sign the voter's blinded ballot token. |
| `POST` | `/api/v1/ballots` | Ballot token and signature | Cast a ballot without the voter's identity. |
| `GET` | `/api/v1/elections/{electionId}/contests/{contestId}/results` | Public after closure | Final contest results. |
| `GET` | `/api/v1/elections/{electionId}/contests/{contestId}/audit` | Public after closure | Verify both hash chains and that they hold the same ballots. |
| `GET` | `/api/v1/elections/{electionId}/contests/{contestId}/ledger` | Public after closure | The shuffled, publicly verifiable ledger. |

List endpoints accept optional `page` and `size` parameters and return the total in the `X-Total-Count` header.

## Project Status
YourVoice is a functional full-stack portfolio MVP. It covers voter onboarding, controlled election registration, geographic eligibility, blind-signature ballot credentials, concurrency-tested duplicate-vote prevention, automatic election lifecycles, per-contest tallying, shuffled and publicly verifiable ledgers, administrator tooling, session security, observability, and automated tests across the backend and frontend.

The React frontend, API and PostgreSQL database are deployed on Heroku; the frontend and API share one origin. The project should not be presented as a certified or operational public-election platform.

## Roadmap
* Add email verification and stronger account recovery.
* Replace the internal cast-order ledger with a verifiable shuffle (mix-net), so not even database administrators can correlate ballot order.
* Move rate limiting to a distributed gateway or a Redis-backed implementation for horizontal scaling.
* Separate application and migration database roles using least privilege.
* Add alerting on the exported metrics, backup restoration tests, and disaster-recovery exercises.
* Add browser end-to-end tests to CI and a formal accessibility audit.
* Anchor signed ledger checkpoints outside the primary database and commission independent cryptographic and penetration testing.
* Expand simulation coverage only where rules can be traced to authoritative South African electoral requirements.

## About the Developer
YourVoice is developed by Humphrey Mahlangu while completing an Information Technology qualification at the Cape Peninsula University of Technology. The project demonstrates full-stack engineering with a security focus: React and TypeScript on the frontend, Java and Spring on the backend, relational data modelling, applied cryptography, API security, automated testing, and production-minded operations.

## Disclaimer
YourVoice is an educational portfolio project and technical simulation. It is not endorsed or certified by the Electoral Commission of South Africa, does not replace statutory election processes, and must not be used to conduct a binding public election.

Online voting requires legal authorization, independent security assessment, operational controls, accessibility review, incident response, physical and procedural safeguards, and public oversight beyond application code alone.

## License
This project is licensed under the MIT License. See [`LICENSE`](LICENSE) for the full license text.

## Fictional profile verification

Authentication creates an account; it does not verify identity or a registered location.
The simulation registry is explicitly fictional and has no IEC, government or official voter-roll
connection. An administrator configures synthetic source records; the voter then requests a
check against the protected identity and name on their account. Existing profiles are never
silently verified. Raw identity numbers are hashed immediately, not stored in the registry.
No identity-document uploads or supporting images are collected.

`GET /api/v1/me/profile` separates identity and location status. The voter can retry
`POST /api/v1/me/profile/verification`, or request an area correction through
`/api/v1/me/profile/corrections`. A correction requires administrator review against the latest
matching fictional source, a reason and an effective date. An established area cannot be changed
by repeatedly checking the source. Geographic identifiers have validated province/municipality
parents; a national-only fictional identity needs neither a municipality nor a ward.

Administration is under `/api/v1/admin/profile-verification` (records, corrections and private
audit) and `/api/v1/admin/geography` (provinces and municipalities). Correction reasons and audit
reasons are redacted after 90 days once closed; pending requests retain the information needed
for review. The audit records the actor, decision, fictional source and effective date, never
ballot choices, tokens or signatures. The registry retains only the synthetic name, protected
identity, configured geography and decision needed to repeat a simulation check. Set
`yourvoice.simulation-verification.enabled=false` to represent an unavailable source; this
must never silently grant verification.


Verified election registrations save an immutable copy of the verified attributes used when joining.
Approved location corrections apply to future registrations, including corrections made before voting
or after a credential is issued. They never add contests to an existing registration. Existing anonymous
credentials remain contest-bound and can still be cast within their original voting window.
Legacy registrations are not automatically verified: while registration is open the voter must explicitly
confirm them against the fictional source. Confirmation can only retain or narrow their original area.
After registration closes, an unconfirmed legacy registration cannot receive a voting credential.

National ballots require verified fictional identity and the existing age/registration/stage checks,
with no local area requirement. Provincial ballots require a verified province; municipal PR also needs
a municipality; ward ballots additionally need a ward in that municipality. MIXED elections contain
independently scoped contests. GET /api/v1/me/ballots returns the authenticated voter's server-derived
eligibility, registration actions, voting actions and reasons. A credential-issued flag is never proof
that a ballot was cast. The fictional source must be available for registration and new/retried issuance;
anonymous casting does not consult identity records.

Historical anonymous ballots remain counted after migration. Existing registrations are unverified
until explicitly confirmed, and are never silently added to the verified turnout denominator. If an
election still has active unverified legacy registrations, final results report their count separately
and leave turnout unavailable rather than inventing a verified denominator. Results and audit remain
subject to the existing completion, contest closure and voting-end checks.

The default fictional source is an empty administrator-managed registry. Set
`YOURVOICE_SIMULATION_VERIFICATION_ENABLED=false` (property `yourvoice.simulation-verification.enabled`)
to make it unavailable; the voter sees that state and cannot obtain new registration/issuance permission.
No government integration is configured. Source records are immutable, keyed by the existing protected
identity hash, and latest sequence wins. Administrators configure an exact fictional name and approved
location hierarchy. Never seed real identity data. Record creation does not verify an account until its
owner explicitly checks. Once a location is established, changing it requires reviewed correction.
Scheduled location approval never reverses a later rejected/pending identity decision.

Voter journey: create a fictional account (local area optional), open **Your profile**, check fictional
verification, request an area correction if necessary, and use **Your ballots** on the dashboard.
An identity-only record can register nationally; missing local attributes affect only contests needing
them. Administration's **Fictional verification, corrections and geographic parents** screen manages
source decisions and effective correction reviews. Existing ballot options represent parties and
independent candidates within a contest; no separate candidate register is implied.

New authenticated API routes include GET/POST `/api/v1/me/profile/corrections`,
POST `/api/v1/me/profile/verification`, GET `/api/v1/me/profile`, GET `/api/v1/me/ballots`,
and administrator-only `/api/v1/admin/profile-verification/{records,corrections,audit}` with
POST `/corrections/{requestId}/review`. Public `/api/v1/geography/{provinces,municipalities}`
returns stable hierarchy IDs; administrator POST routes under `/api/v1/admin/geography/` create parents.
Source records never return identity hashes or raw numbers. Reviews record actor, decision, reason,
source and effective date separately from all ballot records; closed reasons are redacted after 90 days.
