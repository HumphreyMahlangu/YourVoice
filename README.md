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
* Passwords are hashed with BCrypt using a cost factor of 12.
* Role-based authorization separates voter operations from election administration.
* The first-administrator bootstrap flow is disabled by default, token-protected, rate-limited, and permanently closes after an administrator exists.

### Controlled Voter Registration

* Voter onboarding creates the platform account and voter profile together, collecting the legal name, contact email, South African ID, identity-document type, and voting district once.
* Election registration is accepted only while the configured registration window is open and the election is in the correct lifecycle state.
* South African ID numbers are validated during onboarding and stored as peppered HMAC-SHA-256 hashes rather than plaintext identifiers. Election registration reuses the authenticated voter profile instead of asking for the ID again.
* Voting-district and contest-scope checks model national, provincial, municipal proportional-representation, and ward eligibility.

### One Person, One Vote

* Database uniqueness constraints prevent duplicate election registrations, voting rights, anonymous credentials, and ballot positions.
* Pessimistic database locks serialize voting-right, credential-consumption, and ledger-state updates under concurrent requests.
* Each eligible voter receives a one-time anonymous credential for a contest. A successful ballot consumes that credential atomically, preventing replay or duplicate voting.

### Ballot Privacy

* Ballot ledger records contain no account, voter-profile, election-registration, or voting-right foreign key.
* Anonymous credential values are stored as peppered HMAC-SHA-256 hashes and are separated from ballot selections.
* Ballot responses avoid returning ledger positions, hashes, or ballot identifiers that could act as voting receipts.
* Public ledger metadata exposes a coarse recording date rather than an exact cast timestamp to reduce timing correlation.

### Tamper-Evident SHA-256 Ledger

* Every accepted ballot becomes a ledger entry whose SHA-256 hash includes the previous entry hash and canonical ballot data.
* Per-contest ledger state is locked while appending entries, preserving a deterministic chain under concurrent voting.
* Audit operations recompute the chain and compare it with stored ledger state to detect modified, reordered, inserted, or deleted entries.
* The hash chain is tamper-evident, not a blockchain or independently immutable storage system. Stronger real-world assurance would require restricted database administration, external hash anchoring, signed exports, and independent operational oversight.

### Results, Auditing, and Operations

* Results and public ledger views become available only after the election is completed, the contest is closed, and voting has ended.
* Blank and spoilt ballots are represented explicitly and are excluded from valid-vote winner calculations.
* Security audit events record authentication, bootstrap, and rate-limit outcomes without linking anonymous ballot submissions to voter identities.
* OpenAPI documentation, health probes, Flyway migrations, Docker packaging, Postman artifacts, and CI checks support repeatable development and review.

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
| `POST` | `/api/v1/auth/login` | Public | Authenticate an account and return a JWT. |
| `GET` | `/api/v1/auth/me` | Authenticated | Return the current account. |
| `POST` | `/api/v1/admin/bootstrap` | Controlled bootstrap | Create the first administrator during a temporary bootstrap window. |
| `POST` | `/api/v1/admin/voting-districts` | Administrator | Create a voting district. |
| `POST` | `/api/v1/admin/elections` | Administrator | Create an election. |
| `PATCH` | `/api/v1/admin/elections/{electionId}/status` | Administrator | Advance the election lifecycle. |
| `POST` | `/api/v1/admin/elections/{electionId}/contests` | Administrator | Create a geographically scoped contest. |
| `POST` | `/api/v1/admin/elections/{electionId}/contests/{contestId}/options` | Administrator | Add a candidate, party, blank, or spoilt option. |
| `PATCH` | `/api/v1/admin/elections/{electionId}/contests/{contestId}/status` | Administrator | Advance the contest lifecycle. |
| `GET` | `/api/v1/admin/security-audit-events` | Administrator | Review recent security audit events. |
| `GET` | `/api/v1/voting-districts` | Public | List voting districts available during registration. |
| `GET` | `/api/v1/elections` | Public | List elections. |
| `GET` | `/api/v1/elections/{electionId}` | Public | Retrieve an election. |
| `POST` | `/api/v1/elections/{electionId}/registrations` | Authenticated voter | Register the existing voter profile during the election registration window and confirm the voting district. |
| `GET` | `/api/v1/me/registrations` | Authenticated voter | List the current voter's registrations. |
| `GET` | `/api/v1/elections/{electionId}/contests` | Public | List contests and options for an election. |
| `POST` | `/api/v1/elections/{electionId}/contests/{contestId}/credentials` | Authenticated voter | Issue a one-time anonymous voting credential after eligibility checks. |
| `POST` | `/api/v1/ballots` | Anonymous credential | Cast a ballot without attaching the voter's JWT identity. |
| `GET` | `/api/v1/elections/{electionId}/contests/{contestId}/results` | Public after closure | Return final contest results. |
| `GET` | `/api/v1/elections/{electionId}/contests/{contestId}/audit` | Public after closure | Recompute and verify the contest hash chain. |
| `GET` | `/api/v1/elections/{electionId}/contests/{contestId}/ledger` | Public after closure | Return privacy-reduced ledger entries. |

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

The repository also includes Docker Compose and verified Postman artifacts for an end-to-end local demonstration. See [`postman/README.md`](postman/README.md) and [`DEPLOYMENT.md`](DEPLOYMENT.md).

## Project Status

VoteTrust is a functional portfolio MVP. The implemented backend covers account security, controlled election registration, geographic eligibility, anonymous one-time voting credentials, concurrent duplicate-vote prevention, ballot casting, final tallying, SHA-256 ledger verification, OpenAPI documentation, automated tests, and local container packaging.

The project compiles and passes its automated CI quality gates. Cloud deployment automation is present but deployment is currently paused because ACR Tasks are unavailable on the selected Azure free-credit subscription. The repository should not be presented as a certified or operational public-election platform.

## Roadmap

* Select a cloud deployment pipeline compatible with the target Azure subscription and add an entitlement check before provisioning billable resources.
* Add refresh-token rotation, explicit token revocation, and stronger account recovery controls.
* Move rate limiting to a distributed gateway or Redis-backed implementation for horizontal scaling.
* Separate application and migration database roles using least privilege.
* Add centralized observability, alerting, backup restoration tests, and disaster-recovery exercises.
* Anchor signed ledger checkpoints outside the primary database and commission independent cryptographic and penetration testing.
* Expand simulation coverage only where rules can be traced to authoritative South African electoral requirements.

## About the Developer

VoteTrust is developed by Humphrey Mahlangu while completing an Information Technology qualification at the Cape Peninsula University of Technology. The project demonstrates a focus on secure enterprise backend development, Java and Spring engineering, relational data modelling, API security, automated testing, and production-minded operational practices.

## Disclaimer

VoteTrust is an educational portfolio project and technical simulation. It is not endorsed or certified by the Electoral Commission of South Africa, does not replace statutory election processes, and must not be used to conduct a binding public election.

Online voting requires legal authorization, independent security assessment, operational controls, accessibility review, incident response, physical and procedural safeguards, and public oversight beyond application code alone.

## License

This project is licensed under the MIT License. See [`LICENSE`](LICENSE) for the full license text.
