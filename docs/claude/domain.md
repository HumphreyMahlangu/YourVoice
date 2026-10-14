# Domain model and core flows

All IDs are UUIDs (`GenerationType.UUID`). All timestamps are `Instant` / `TIMESTAMPTZ`, UTC.

## Entities → tables

| Entity | Table | Key fields / constraints | Introduced |
| --- | --- | --- | --- |
| `UserAccount` | `user_accounts` | email (unique, + unique `LOWER(email)` index), `password_hash` (BCrypt-12), role `VOTER`/`ADMIN`, enabled | V1 |
| `VotingDistrict` | `voting_districts` | `code` unique (stored upper-case), name, province, municipality, `ward_number` | V2 |
| `Election` | `elections` | name, type `NATIONAL`/`PROVINCIAL`/`MUNICIPAL`, 4 window instants, status. DB checks start<end for both windows | V2 |
| `VoterProfile` | `voter_profiles` | 1:1 `user_account_id` (unique), `id_number_hash` (unique, HMAC hex 64), `date_of_birth`, `voting_district_id`, `full_name`, `id_document_type` (V10, nullable in DB) | V2/V4/V10 |
| `ElectionRegistration` | `election_registrations` | (voter_profile, election) unique, `voting_district_id`, status `ACTIVE`/`CANCELLED`, registered_at | V2 |
| `Contest` | `contests` | election, name (unique per election), type, status `DRAFT`/`OPEN`/`CLOSED`, display_order, `scope_province/municipality/ward_number` with a DB check per type | V3/V5 |
| `ContestOption` | `contest_options` | contest, name + display_order unique per contest, `option_type`. Partial unique index: at most one `BLANK_BALLOT` and one `SPOILT_BALLOT` per contest | V3/V8 |
| `VotingRight` | `voting_rights` | (voter_profile, contest) unique, `credential_issued` boolean. **No timestamps** (dropped in V6 for privacy) | V3/V6 |
| `AnonymousVotingCredential` | `anonymous_voting_credentials` | contest, `credential_hash` unique, `expires_at`, `used` boolean. **No voter link, no issued/used timestamps** | V3/V6 |
| `LedgerState` | `ledger_states` | 1 per contest: `current_hash` (starts at 64 zeros = `GENESIS_HASH`), `next_ledger_index` | V3 |
| `BallotLedgerEntry` | `ballot_ledger_entries` | contest, contest_option, ledger_index (unique per contest), previous_hash, current_hash, nonce, cast_at. **No voter link** | V3 |
| `SecurityAuditEvent` | `security_audit_events` | event_type, outcome `SUCCESS`/`FAILURE`/`BLOCKED`, principal id/email, client_ip, user_agent, detail, occurred_at | V7 |
| `ElectionLifecycleEvent` | `election_lifecycle_events` | `event_sequence` (DB identity, read-only in JPA), election_id (plain UUID column, not a relation), previous/new status, trigger `AUTOMATIC`/`ADMINISTRATOR`, outcome `SUCCESS`/`FAILURE`, actor, detail | V9 |

`ContestOptionType.isValidVote()` is true for `PARTY` and `INDEPENDENT_CANDIDATE`, false for `BLANK_BALLOT` and `SPOILT_BALLOT`.

## Election lifecycle (automatic)

```
DRAFT ──(now ≥ registrationStartAt, ballot ready)──▶ REGISTRATION_OPEN
      ──(now ≥ registrationEndAt)──────────────────▶ REGISTRATION_CLOSED
      ──(now ≥ votingStartAt; all DRAFT contests→OPEN)▶ VOTING_OPEN
      ──(now ≥ votingEndAt; all contests→CLOSED)────▶ COMPLETED
any non-COMPLETED ──(admin PATCH status=CANCELLED; OPEN contests→CLOSED)──▶ CANCELLED
```

- `ElectionLifecycleCoordinator` is `@Scheduled` with a fixed delay of `votetrust.lifecycle.poll-interval-ms` (default 5000).
  On each tick it loads the IDs of elections in DRAFT/REG_OPEN/REG_CLOSED/VOTING_OPEN and calls
  `ElectionLifecycleService.advanceElection(id)`, one transaction per election, with `SELECT … FOR UPDATE` on the election.
  The coordinator is disabled with `votetrust.lifecycle.scheduling-enabled=false`, which the test profile sets.
- `advanceDueTransitions` runs its `if` blocks in sequence, so one tick can cascade through several states if the
  service was down for a while.
- **Ballot ready** means at least 1 contest, and every contest has at least 2 *valid-vote* options (party or
  independent). This is checked before REGISTRATION_OPEN and again before VOTING_OPEN. Voting also refuses to open if
  any contest is already CLOSED. Completion requires at least 1 contest.
- **Fail closed**: an `ElectionLifecycleException` during an automatic transition leaves the status unchanged and
  records **one** `FAILURE` event per (election, previousStatus). After that it retries silently every tick.
- Every successful transition writes a `SUCCESS` lifecycle event. Admin cancellation records the actor's id and email.
- Admin `PATCH /admin/elections/{id}/status` accepts only `CANCELLED`; anything else is a 409. Cancelling a CANCELLED
  election is an idempotent no-op; cancelling a COMPLETED one is a 409.
- Admin `PATCH …/contests/{cid}/status` is effectively a check: it returns 200 if the requested status equals the
  current one and 409 otherwise.

## Ballot configuration rules (admin)

- Election creation: name unique (case-insensitive), `regStart < regEnd ≤ votingStart < votingEnd`. Created as DRAFT.
- Contest and option creation is allowed only while the election is `DRAFT` **and** `now < registrationEndAt`.
  Options also require the contest to be `DRAFT`. Configuring after `registrationStartAt` is the recovery path for
  an election whose automatic opening failed because the ballot wasn't ready: the next scheduler tick opens
  registration once it is. After `registrationEndAt`, a still-DRAFT election can only be cancelled.
  Messages: "…locked once registration opens" (not DRAFT) and "…closed because the registration window has ended…".
- Contest types allowed per election type: NATIONAL → NATIONAL or PROVINCIAL; PROVINCIAL → PROVINCIAL;
  MUNICIPAL → MUNICIPAL_PR or MUNICIPAL_WARD.
- Required scope per contest type (matches the V5 DB check):

  | Contest type | scopeProvince | scopeMunicipality | scopeWardNumber |
  | --- | --- | --- | --- |
  | NATIONAL | null | null | null |
  | PROVINCIAL | ✔ | null | null |
  | MUNICIPAL_PR | ✔ | ✔ | null |
  | MUNICIPAL_WARD | ✔ | ✔ | ✔ |

- Contest names and display orders are unique per election. Option names and display orders are unique per contest.
  There is at most one BLANK and one SPOILT option per contest.
- Nothing can be updated or deleted: there are no edit or delete endpoints for any admin resource.

## Voter onboarding and registration

1. **Account registration** (`AuthService.register`) creates the `UserAccount` (role VOTER) and the `VoterProfile`
   together. `SouthAfricanIdNumberValidator` requires 13 digits, a valid YYMMDD date of birth (century inferred: 20YY
   unless that's in the future, then 19YY), a valid Luhn-style checksum, citizenship digit (index 10) == `0`, and
   **age ≥ 16** at registration. The raw ID is HMAC-hashed with `VOTETRUST_ID_HASH_PEPPER`, and a duplicate hash
   gives a 409. Returns a JWT immediately.
2. **Election registration** (`VoterRegistrationService`) requires election `REGISTRATION_OPEN` **and** `now` within
   `[registrationStartAt, registrationEndAt]` (inclusive). The registration **always uses the `VoterProfile`'s
   district**. The request body is optional (`{}` or none). If it includes a `votingDistrictId` that differs from
   the profile's, the result is 422 "Voting district must match the district on your voter profile". There's no
   API to change a profile's district yet. One registration per (profile, election).

## Voting flow (anonymity design)

```
Voter (JWT) ── POST /elections/{e}/contests/{c}/credentials ──▶ VotingService.issueVotingCredential
   checks: contest∈election; election VOTING_OPEN & contest OPEN & now∈[votingStart, votingEnd];
           ACTIVE registration; age ≥ 18 on votingStartAt's UTC date; registration district matches contest scope
   VotingRight (profile,contest) locked FOR UPDATE / created; if credentialIssued → 409
   raw = 32 random bytes base64url; store HMAC(raw, VOTE_CREDENTIAL_PEPPER) + expiresAt = votingEndAt
   VotingRight.credentialIssued = true   ← the only link between voter and contest; says nothing about the ballot
   ◀── { votingCredential: raw, expiresAt }   (the only time raw is ever visible)

Anyone (no JWT) ── POST /ballots { contestId, contestOptionId, votingCredential } ──▶ VotingService.castBallot
   option∈contest; voting open (same checks); credential looked up by HMAC + contest FOR UPDATE
   used → 409 "already been used"; unknown/expired → 401
   credential.markUsed(); VoteHashChainService.appendVote(...)
   ◀── { contestId, accepted: true, message }   (no receipt)
```

District eligibility: NATIONAL is always eligible. Province, municipality and ward are compared case-insensitively
after trimming, against the registration's voting district, which is always the profile's district.

## Hash-chain ledger

`VoteHashChainService.appendVote` locks the contest's `LedgerState` (creating it at genesis on the first vote), then computes

```
SHA-256( "votetrust-ledger-v1|{contestId}|{optionId}|{ledgerIndex}|{previousHash}|{nonce}|{castAt ISO-8601}" )  → lowercase hex
```

The nonce is 24 random bytes, base64url without padding. It saves the entry and advances the state (`currentHash`,
`nextLedgerIndex++`). This is tamper-*evident* only. Anyone with DB access can rewrite the whole chain consistently.

`TallyAuditService.verifyContestLedger` replays entries in index order and reports violations for index gaps,
`previousHash` mismatch, recomputed hash mismatch, a missing state, a stored head ≠ computed head, or a stored
next index ≠ entry count. `chainValid = violations.isEmpty()`.

## Results

`TallyAuditService.getContestResult` uses a JPQL left join from options to ledger entries, grouped per option.
- `validVotes` = sum over PARTY and INDEPENDENT options. Blank and spoilt are counted separately and excluded from
  `options[]` in the response.
- `ballotsCast` = all ledger entries for the contest. `registeredVoterCount` = ACTIVE registrations for the
  **whole election**, which is also the turnout denominator.
- Percentages are `BigDecimal`, scale 2, HALF_UP. `leading` = voteCount equals the max and the max is > 0, so ties
  give several leaders.
- The public ledger (`listContestLedger`) exposes `recordedDate` (a UTC `LocalDate`) instead of `castAt`, to reduce
  timing correlation. Note that a client can't recompute hashes from the public ledger alone because `castAt` is needed.

## Security audit events

`SecurityAuditService.record` runs in `REQUIRES_NEW`, so the audit row persists even if the caller's transaction
rolls back. Events: `USER_REGISTER`, `USER_LOGIN`, `ADMIN_BOOTSTRAP`, `RATE_LIMIT_BLOCKED`. Voting and credential
actions are **intentionally not audited**, for anonymity. Admins read the latest N events (capped at 100).

## Admin bootstrap

`POST /api/v1/admin/bootstrap` with header `X-VoteTrust-Bootstrap-Token`. It requires
`votetrust.admin.bootstrap.enabled=true` (default false; startup fails if enabled with a token under 32 chars),
a constant-time token match, no existing ADMIN, and an unused email. Disabled → 403, bad token → 401, admin exists → 409.
It's the only way to create an admin. There is no admin-management API.
