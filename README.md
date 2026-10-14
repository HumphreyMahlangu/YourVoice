# VoteTrust
**A Secure Digital Voting Platform**
Exploring how modern software engineering, cryptography, and secure system design can improve transparency, accessibility, and trust in digital voting.

## Table of Contents
* [Overview](#overview)
* [Why VoteTrust?](#why-votetrust)
* [The Problem](#the-problem)
* [Project Vision](#project-vision)
* [Project Goals](#project-goals)
* [Core Principles](#core-principles)
* [Key Features](#key-features)
* [Technology Stack](#technology-stack)
* [System Architecture / Endpoints](#system-architecture--endpoints)
* [Local Deployment](#local-deployment)
* [Project Status](#project-status)
* [Roadmap](#roadmap)
* [About the Developer](#about-the-developer)
* [Disclaimer](#disclaimer)
* [License](#license)

## Overview
VoteTrust is a backend-focused software engineering project that explores how secure digital voting systems can be designed using modern technologies.
The project investigates the use of secure authentication, cryptography, audit logging, and distributed ledger concepts to build a voting platform that prioritizes integrity, transparency, privacy, and accessibility.
Rather than attempting to replace existing election systems, VoteTrust serves as a technical exploration of the challenges involved in designing a secure digital voting platform while following modern software engineering best practices.
The primary objective is to demonstrate robust backend architecture, secure application development, and thoughtful system design.

## Why VoteTrust?
Digital technology has transformed how people bank, communicate, study, shop, and access government services. However, voting remains a largely physical process in many parts of the world.
While in-person voting plays an important role in maintaining election integrity, practical barriers such as long queues, travel requirements, accessibility challenges, and time constraints may discourage participation for some eligible voters.
VoteTrust was created to explore an important engineering question:
How can modern software technologies improve accessibility to voting while preserving security, privacy, transparency, and public trust?
This project does not claim to solve every challenge associated with online voting. Instead, it investigates how secure software engineering principles can contribute to future digital voting systems.

## The Problem
Designing a secure digital voting system is significantly more complex than building a standard web application.
A trustworthy voting platform must address challenges such as:
* Verifying voter identity.
* Preventing duplicate voting.
* Protecting ballot secrecy.
* Preventing unauthorized modification of votes.
* Providing transparent and auditable election records.
* Preserving voter privacy.
* Maintaining confidence in election results.
VoteTrust explores these challenges through software engineering, security, and modern backend architecture.

## Project Vision
The vision of VoteTrust is to build a secure, scalable, and maintainable backend platform that demonstrates how modern technologies can be combined to create a transparent and trustworthy digital voting system.
The project emphasizes clean architecture, security-first development, and real-world engineering practices over simply implementing features.

## Project Goals
* Build a secure RESTful API using Spring Boot.
* Implement robust authentication and authorization.
* Ensure every eligible voter can cast only one vote.
* Protect ballot confidentiality through cryptographic techniques.
* Maintain immutable and verifiable audit records.
* Apply secure software engineering principles throughout the system.
* Demonstrate scalable backend architecture suitable for enterprise applications.
* Produce a professional portfolio project that reflects real-world engineering practices.

## Core Principles
VoteTrust is built around five fundamental principles.
* **Security**: Protect voter identities, ballots, and election data against unauthorized access and tampering.
* **Privacy**: Ensure that individual votes remain confidential while maintaining election integrity.
* **Integrity**: Guarantee that votes cannot be modified, duplicated, or removed once accepted by the system.
* **Transparency**: Provide verifiable audit logs that improve accountability without exposing sensitive information.
* **Accessibility**: Explore how digital platforms may reduce practical barriers that discourage participation.

---

## Key Features

### Stateless Authentication and Authorization

* Spring Security operates without server-side HTTP sessions and authenticates requests with short-lived JWT access tokens.
* Rotating refresh tokens (stored hashed, grouped in families) keep sessions alive across page reloads; reusing a rotated token revokes the whole family, and logout revokes it explicitly.
* Passwords are hashed with BCrypt using a cost factor of 12. Unknown emails still pay for one BCrypt check, so response timing does not reveal which accounts exist.
* Repeated failed sign-ins lock an email address temporarily, in addition to per-IP rate limits. The lock applies equally to unknown emails.
* Role-based authorization separates voter operations from election administration. The first-administrator bootstrap is disabled by default, token-protected, and closes after an administrator exists; administrators can then add further administrators, and each addition is audited.

### Controlled Voter Registration

* Voter onboarding creates the platform account and voter profile together, collecting the legal name, contact email, South African ID, identity-document type, and voting district once.
* Election registration is accepted only while the configured registration window is open and the election is in the correct lifecycle state. It always uses the voting district captured at onboarding, so voters cannot choose a different ward or province per election.
* South African ID numbers are validated during onboarding and stored as peppered HMAC-SHA-256 hashes rather than plaintext identifiers.
* Voting-district and contest-scope checks model national, provincial, municipal proportional-representation, and ward eligibility.

### Unlinkable Ballot Credentials (Blind Signatures)

* Each contest has its own RSA key. The voter's browser generates a random ballot token, blinds it, and asks the server to sign the blinded value after the eligibility checks.
* The browser removes the blinding and casts the ballot with the token and signature, without the voter's login token. The server can verify the signature but cannot link the ballot to the voter it signed for.
* Each voter can obtain one signature per contest; repeating a request with the same blinded token returns the same signature, so a lost response can be retried safely.
* Private keys are encrypted at rest (AES-256-GCM), and every voter can compare the contest key fingerprint.

### One Person, One Vote

* Database uniqueness constraints prevent duplicate election registrations, voting rights, spent ballot tokens, and ledger positions.
* Pessimistic locks and race-free inserts serialize voting rights, token spending, and ledger appends. Dedicated concurrency tests release many simultaneous requests against PostgreSQL and require exactly one success.

### Ballot Privacy

* Ballot ledgers contain no account, voter-profile, election-registration, or voting-right reference.
* The public ledger is published after voting closes with ballots shuffled into a random order, so ledger positions say nothing about when (or by whom) a ballot was cast.
* The database stores only the day each ballot was recorded, never the exact time.
* Ballot responses contain no receipt, and no metric is broken down by option.

### Tamper-Evident, Publicly Verifiable Ledgers

* Ballots are chained with SHA-256 in an internal ledger (cast order) and, after closing, in a published ledger (shuffled order) whose hash inputs are all public.
* Anyone can recompute the published chain; the public ledger page does it in the browser. The server audit also checks the internal chain and that both ledgers contain the same ballots.
* The hash chains are tamper-evident, not a blockchain. Stronger real-world assurance would require restricted database administration, external hash anchoring, signed exports, and independent operational oversight.

### Results, Auditing, and Operations

* Results and ledgers become available only after the election is completed, the contest is closed, and voting has ended. Turnout is computed against the voters eligible for each contest.
* Blank and spoilt ballots are represented explicitly and are excluded from valid-vote winner calculations.
* Election stages advance automatically on schedule; administrators can correct draft ballots, reschedule draft elections, or cancel an election, and every change is recorded.
* Security audit events record authentication, session, bootstrap, administrator, and rate-limit outcomes without linking ballots to voters.
* Every response carries a request ID that also appears in logs; Prometheus metrics (admin only), optional JSON logs, health probes, Flyway migrations, Docker packaging, Postman artifacts, and CI for backend and frontend support operations.

## Technology Stack

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| Application framework | Spring Boot 3 |
| Web API | Spring MVC and Jakarta Bean Validation |
| Security | Spring Security, stateless JWT with JJWT, BCrypt |
| Persistence | Spring Data JPA and Hibernate |
| Database | PostgreSQL 16 |
| Schema management | Flyway |
| API documentation | Springdoc OpenAPI and Swagger UI |
| Build | Maven Wrapper |
| Testing | JUnit 5, Mockito, Spring Security Test, Testcontainers |
| Operations | Spring Boot Actuator, Docker, Docker Compose |
| CI | GitHub Actions |

Runtime secrets and database credentials are supplied through environment variables. They are not embedded in the application artifact.

## System Architecture / Endpoints

VoteTrust follows a layered Spring architecture:

```text
HTTP client
    -> Spring Security filters and rate limiting
    -> REST controllers and validated DTOs
    -> transactional domain services
    -> Spring Data JPA repositories
    -> PostgreSQL and Flyway-managed schema
```

The system uses a centralized PostgreSQL ledger. It applies cryptographic chaining for tamper evidence but does not claim to be a distributed ledger or blockchain.

### Core REST API

| Method | Endpoint | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Public | Create a voter account and protected voter profile, then return a JWT. |
| `POST` | `/api/v1/auth/login` | Public | Authenticate an account and return an access and refresh token. |
| `POST` | `/api/v1/auth/refresh` | Refresh token | Rotate the refresh token and issue a new access token. |
| `POST` | `/api/v1/auth/logout` | Refresh token | Revoke the session. |
| `GET` | `/api/v1/auth/me` | Authenticated | Return the current account. |
| `POST` | `/api/v1/admin/bootstrap` | Controlled bootstrap | Create the first administrator during a temporary bootstrap window. |
| `GET`, `POST` | `/api/v1/admin/accounts` | Administrator | List or add administrators. |
| `POST`, `PUT`, `DELETE` | `/api/v1/admin/voting-districts[/{id}]` | Administrator | Manage voting districts (geography is locked once voters use a district). |
| `POST` | `/api/v1/admin/elections` | Administrator | Create an election. |
| `PUT` | `/api/v1/admin/elections/{electionId}` | Administrator | Rename or reschedule a draft election. |
| `PATCH` | `/api/v1/admin/elections/{electionId}/status` | Administrator | Emergency-cancel an election (other transitions are automatic). |
| `POST`, `PUT`, `DELETE` | `/api/v1/admin/elections/{electionId}/contests[/{contestId}]` | Administrator | Manage geographically scoped contests while the ballot is a draft. |
| `POST`, `PUT`, `DELETE` | `/api/v1/admin/elections/{electionId}/contests/{contestId}/options[/{optionId}]` | Administrator | Manage party, candidate, blank, or spoilt options while the ballot is a draft. |
| `GET` | `/api/v1/admin/security-audit-events` | Administrator | Review recent security audit events. |
| `GET` | `/api/v1/voting-districts` | Public | List voting districts available during registration. |
| `GET` | `/api/v1/elections` | Public | List elections. |
| `GET` | `/api/v1/elections/{electionId}` | Public | Retrieve an election. |
| `POST` | `/api/v1/elections/{electionId}/registrations` | Authenticated voter | Register the existing voter profile, in its onboarding voting district, during the election registration window. |
| `GET` | `/api/v1/me/registrations` | Authenticated voter | List the current voter's registrations. |
| `GET` | `/api/v1/elections/{electionId}/contests` | Public | List contests and options for an election. |
| `GET` | `/api/v1/elections/{electionId}/contests/{contestId}/ballot-key` | Public once voting opens | Return the contest's public RSA key for blinding. |
| `POST` | `/api/v1/elections/{electionId}/contests/{contestId}/credentials` | Authenticated voter | Blind-sign the voter's blinded ballot token after eligibility checks. |
| `POST` | `/api/v1/ballots` | Ballot token and signature | Cast a ballot without attaching the voter's identity. |
| `GET` | `/api/v1/elections/{electionId}/contests/{contestId}/results` | Public after closure | Return final contest results. |
| `GET` | `/api/v1/elections/{electionId}/contests/{contestId}/audit` | Public after closure | Verify both hash chains and that they contain the same ballots. |
| `GET` | `/api/v1/elections/{electionId}/contests/{contestId}/ledger` | Public after closure | Return the shuffled, publicly verifiable ledger. |

List endpoints accept optional `page` and `size` parameters and return the total in the `X-Total-Count` header.

Interactive API documentation is available at `/swagger-ui.html`; the OpenAPI document is available at `/api-docs`.

## Local Deployment

### Prerequisites

* Git
* Java 21 or newer
* PostgreSQL 16 or a compatible supported PostgreSQL installation
* PowerShell on Windows, or a POSIX-compatible shell on Linux/macOS

### 1. Clone the repository

```powershell
git clone https://github.com/HumphreyMahlangu/VoteTrust.git
Set-Location VoteTrust
```

### 2. Create the local PostgreSQL database

Open `psql` as a PostgreSQL administrator and create a dedicated local role and database:

```sql
CREATE USER votetrust WITH PASSWORD 'replace-with-a-local-password';
CREATE DATABASE votetrust OWNER votetrust;
```

Do not reuse development credentials in any hosted environment.

### 3. Configure the application

Set the required values in the same PowerShell session that will start Spring Boot:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/votetrust"
$env:SPRING_DATASOURCE_USERNAME = "votetrust"
$env:SPRING_DATASOURCE_PASSWORD = "replace-with-the-local-database-password"
$env:VOTETRUST_JWT_SECRET = "replace-with-a-random-secret-of-at-least-32-characters"
$env:VOTETRUST_ID_HASH_PEPPER = "replace-with-an-independent-random-32-character-pepper"
$env:VOTETRUST_VOTE_CREDENTIAL_PEPPER = "replace-with-another-independent-32-character-pepper"
```

Keep the JWT secret and two peppers independent. Never commit real values to `.env`, source code, Postman files, or GitHub.

### 4. Run tests

```powershell
.\mvnw.cmd clean verify
```

Integration tests use PostgreSQL Testcontainers when Docker is available. GitHub Actions runs the complete database-backed suite with PostgreSQL.

### 5. Start the API

```powershell
.\mvnw.cmd spring-boot:run
```

On Linux or macOS, use `./mvnw` instead of `.\mvnw.cmd`.

### 6. Verify the service

* API base URL: `http://localhost:8080`
* Readiness: `http://localhost:8080/actuator/health/readiness`
* Liveness: `http://localhost:8080/actuator/health/liveness`
* Swagger UI: `http://localhost:8080/swagger-ui.html`
* OpenAPI JSON: `http://localhost:8080/api-docs`

The repository also includes Docker Compose and verified Postman artifacts for an end-to-end local demonstration. See [`postman/README.md`](postman/README.md) and [`DEPLOYMENT.md`](DEPLOYMENT.md), which also covers the Render (API) and Vercel (frontend) deployments.

## Project Status

VoteTrust is a functional portfolio MVP. The implemented system covers account security with rotating sessions, controlled election registration, geographic eligibility, blind-signature ballot credentials, concurrency-tested duplicate-vote prevention, ballot casting, per-contest tallying, shuffled and publicly verifiable ledgers, administrator tooling, observability, OpenAPI documentation, automated backend and frontend tests, and container packaging.

The project compiles and passes its automated CI quality gates. The API and PostgreSQL database are deployed to Render through the `render.yaml` Blueprint, and the React frontend is deployed to Vercel. The repository should not be presented as a certified or operational public-election platform.

## Roadmap

* Add email verification and stronger account recovery controls.
* Replace the internal cast-order ledger with a verifiable shuffle (mix-net) so not even database administrators can correlate ballot order.
* Move rate limiting to a distributed gateway or Redis-backed implementation for horizontal scaling.
* Separate application and migration database roles using least privilege.
* Add alerting on the exported metrics, backup restoration tests, and disaster-recovery exercises.
* Anchor signed ledger checkpoints outside the primary database and commission independent cryptographic and penetration testing.
* Expand simulation coverage only where rules can be traced to authoritative South African electoral requirements.

## About the Developer

VoteTrust is developed by Humphrey Mahlangu while completing an Information Technology qualification at the Cape Peninsula University of Technology. The project demonstrates a focus on secure enterprise backend development, Java and Spring engineering, relational data modelling, API security, automated testing, and production-minded operational practices.

## Disclaimer

VoteTrust is an educational portfolio project and technical simulation. It is not endorsed or certified by the Electoral Commission of South Africa, does not replace statutory election processes, and must not be used to conduct a binding public election.

Online voting requires legal authorization, independent security assessment, operational controls, accessibility review, incident response, physical and procedural safeguards, and public oversight beyond application code alone.

## License

This project is licensed under the MIT License. See [`LICENSE`](LICENSE) for the full license text.
