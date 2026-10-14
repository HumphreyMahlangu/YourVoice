# Domain model and core flows

All IDs are UUIDs. All timestamps are `Instant` / `TIMESTAMPTZ`, in UTC. The latest migration is **V14**.

## Entities and tables

| Entity | Table | Key fields / constraints | Migrations |
| --- | --- | --- | --- |
| `UserAccount` | `user_accounts` | email (unique, plus a unique `LOWER(email)` index), `password_hash` (BCrypt-12), role `VOTER`/`ADMIN`, enabled | V1 |
| `VotingDistrict` | `voting_districts` | `code` unique (stored upper-case), name, province, municipality, `ward_number` | V2 |
| `Election` | `elections` | name, type `NATIONAL`/`PROVINCIAL`/`MUNICIPAL`, four window instants, status | V2 |
| `VoterProfile` | `voter_profiles` | 1:1 user account, `id_number_hash` (unique HMAC hex), `date_of_birth`, `voting_district_id`, `full_name`, `id_document_type` | V2/V4/V10 |
| `ElectionRegistration` | `election_registrations` | (profile, election) unique, `voting_district_id` (always the profile's district), status, registered_at | V2 |
| `Contest` | `contests` | election, name and display_order unique per election, type, status `DRAFT`/`OPEN`/`CLOSED`, scope columns (DB check per type) | V3/V5 |
| `ContestOption` | `contest_options` | name and display_order unique per contest, `option_type`; at most one BLANK and one SPOILT per contest | V3/V8 |
| `VotingRight` | `voting_rights` | (profile, contest) unique, `credential_issued`, `blinded_token_hash`. **No timestamps** | V3/V6/V12 |
| `AnonymousVotingCredential` | `anonymous_voting_credentials` | Now records **spent ballot tokens**: contest, `credential_hash` = HMAC(pepper, contestId\|token), unique, `used`. No voter link | V3/V6 |
| `ContestSigningKey` | `contest_signing_keys` | one per contest: RSA modulus and exponent (base64url), AES-GCM-encrypted PKCS#8 private key. Read-only entity, inserted natively | V12 |
| `LedgerState` | `ledger_states` | per contest: internal `current_hash`, `next_ledger_index`, plus `published_head_hash` and `published_entry_count` once published | V3/V11 |
| `BallotLedgerEntry` | `ballot_ledger_entries` | internal chain in cast order: option, ledger_index, previous/current hash, nonce, `cast_at`, `hash_version` (1 or 2) | V3/V11 |
| `PublishedBallotEntry` | `published_ballot_entries` | public chain in **shuffled** order: option, position, previous/current hash, fresh nonce | V11 |
| `RefreshToken` | `refresh_tokens` | user, `family_id`, SHA-256 `token_hash` (unique), expires_at, revoked_at | V13 |
| `SecurityAuditEvent` | `security_audit_events` | event_type (no DB check, so new enum values need no migration), outcome, principal, client_ip, user_agent, detail | V7 |
| `ElectionLifecycleEvent` | `election_lifecycle_events` | `event_sequence` identity, election_id, previous/new status, trigger, outcome, actor, detail | V9 |
| (ShedLock) | `shedlock` | distributed lock for the lifecycle scheduler | V14 |

## Election lifecycle (automatic)

```
DRAFT ──(now ≥ registrationStartAt, ballot ready)──▶ REGISTRATION_OPEN
      ──(now ≥ registrationEndAt)──────────────────▶ REGISTRATION_CLOSED
      ──(now ≥ votingStartAt; DRAFT contests→OPEN, ballot keys generated)▶ VOTING_OPEN
      ──(now ≥ votingEndAt; contests→CLOSED, ledgers published)────────▶ COMPLETED
any non-COMPLETED ──(admin cancel; OPEN contests→CLOSED)──▶ CANCELLED
```

- `ElectionLifecycleCoordinator` runs every `votetrust.lifecycle.poll-interval-ms` (default 5 s) under a ShedLock
  lock, so only one instance polls at a time. Each election advances in its own transaction with
  `SELECT … FOR UPDATE`, and one tick can cascade through several states.
- **Ballot ready** means at least one contest, and every contest has at least two party or independent options.
  This is checked before registration opens and before voting opens.
- **Fail closed:** a failed transition leaves the status unchanged and writes a `FAILURE` event, unless the
  election's *latest* event is already the same failure. Any later event, such as an admin reschedule, re-arms
  recording. The `votetrust.elections.stuck` gauge counts active elections whose latest event is a failure from
  their current status.
- Admins can cancel (PATCH status `CANCELLED`) and can **rename or reschedule a DRAFT election**
  (`PUT /admin/elections/{id}`). A reschedule needs a registration window ending in the future, and it is
  recorded as an `ADMINISTRATOR` lifecycle event with status unchanged. Rescheduling is how an election that
  missed its registration window gets a new one.

## Ballot configuration (admin)

- Allowed while the election is `DRAFT` **and** `now < registrationEndAt`. Configuring after
  `registrationStartAt` is the recovery path for an election that wasn't ready in time: the next scheduler tick
  opens registration once it is.
- Contests and options can be created, replaced (PUT, full body) and deleted. Deleting a contest deletes its
  options. Option changes also require the contest to be `DRAFT`.
- Contest types allowed per election type: NATIONAL → NATIONAL or PROVINCIAL; PROVINCIAL → PROVINCIAL;
  MUNICIPAL → MUNICIPAL_PR or MUNICIPAL_WARD. Scope rules (also enforced by a DB check):

  | Contest type | scopeProvince | scopeMunicipality | scopeWardNumber |
  | --- | --- | --- | --- |
  | NATIONAL | null | null | null |
  | PROVINCIAL | required | null | null |
  | MUNICIPAL_PR | required | required | null |
  | MUNICIPAL_WARD | required | required | required |

- Districts can be edited and deleted. Once any voter profile or registration uses a district, only its code and
  name may change, because geography drives eligibility, and it can't be deleted.
- Admins: the first comes from the bootstrap. After that, admins create admins (`POST /admin/accounts`), and
  each creation is audited as `ADMIN_ACCOUNT_CREATED` with the acting admin.

## Voter onboarding and registration

1. **Account registration** creates the `UserAccount` (VOTER) and the `VoterProfile` together. The SA ID is
   validated (13 digits, a real date of birth, Luhn-style checksum, citizenship digit `0`, age ≥ 16), then
   HMAC-hashed. A duplicate email *or* ID returns the same generic 409 message.
2. **Election registration** requires `REGISTRATION_OPEN` and `now` inside the window. It **always uses the
   profile's district**. The body is optional; a `votingDistrictId` that differs from the profile's gets a 422.

## Voting flow: blind signatures

The server issues a credential to an authenticated voter but can't link the resulting ballot to them.

```
GET  /elections/{e}/contests/{c}/ballot-key            (public, once the contest is OPEN)
     ◀── { modulus n, publicExponent e, keyFingerprint, tokenDomain }

Browser: t = 32 random bytes (base64url); r random, coprime with n
         H(t) = MGF1-SHA256("votetrust-ballot-token-v1|{contestId}|{t}", len(n)+16 bytes) mod n
         blinded = H(t) · r^e mod n

POST /elections/{e}/contests/{c}/credentials  { blindedToken }   (JWT)
     checks: voting open; ACTIVE registration; age ≥ 18 on votingStart's date; district eligible
     VotingRight inserted ON CONFLICT DO NOTHING, then locked FOR UPDATE
       already issued with the same blindedTokenHash → re-sign (idempotent retry)
       already issued with a different one          → 409
     s' = blinded^d mod n   (JCA raw RSA, which applies RSA blinding against timing attacks)
     ◀── { blindSignature, keyFingerprint, expiresAt }

Browser: s = s' · r^-1 mod n; checks s^e ≡ H(t) before using it

POST /ballots  { contestId, contestOptionId, ballotToken: t, signature: s }   (no JWT)
     verify s^e ≡ H(t) (mod n) for that contest's key, else 401
     insert HMAC(contestId|t) into anonymous_voting_credentials ON CONFLICT DO NOTHING; 0 rows → 409 "already been used"
     append to the internal ledger
```

Notes:
- The blinded token is uniformly random given `r`, so storing its hash on `VotingRight` reveals nothing about `t`.
- Keys are per contest (generated when the contest opens, or lazily for older open contests). The private key
  is encrypted with AES-256-GCM under `HMAC-SHA256(vote-credential pepper, "votetrust-ballot-signing-key-encryption-v1")`,
  with the contest id as associated data. **Rotating the vote-credential pepper makes the keys of open contests
  undecryptable.**
- One key per contest is what stops the server from tagging voters with different keys. Voters can compare
  `keyFingerprint`, which the vote page shows after a successful ballot.

## Ledgers

**Internal ledger** (cast order, server-side tamper evidence):
`SHA-256("votetrust-ledger-v{1|2}|contestId|optionId|ledgerIndex|previousHash|nonce|castAt")`.
New entries are v2, which stores and hashes `cast_at` truncated to the UTC day. v1 entries (exact instant)
still verify. `VoteHashChainService.lockLedgerState` creates the state with `ON CONFLICT DO NOTHING`, locks it
`FOR UPDATE`, then **refreshes the entity**. Without that refresh, an instance already cached in the persistence
context would hide another transaction's changes; the concurrency tests caught this.

**Published ledger** (built once per contest when the election completes, or lazily on the first
results/audit/ledger read): the ballots are shuffled with `SecureRandom`, given fresh nonces, and chained as
`SHA-256("votetrust-public-ledger-v1|contestId|optionId|position|previousHash|nonce")`. Every input is public, so
anyone can verify it, and the frontend ledger page does so in the browser. Its order reveals nothing about cast
order. Appends check the published flag under the ledger-state lock, so no ballot can land after publication.

**Audit** (`TallyAuditService.verifyContestLedger`) checks the internal chain, the published chain, the stored
heads and counts, and that both ledgers hold the same multiset of options.

## Results

- `validVotes` covers PARTY and INDEPENDENT options. Blank and spoilt ballots are counted separately and
  excluded from `options[]`.
- `registeredVoterCount` and `turnoutPercentage` count **only registrations eligible for that contest**
  (grouped by district, filtered by `ContestEligibility`, the same rule credential issuance uses).
- `ledgerHeadHash` is the **published** ledger head.
- Results, audit and ledger stay hidden until the election is COMPLETED, the contest CLOSED, and `now > votingEndAt`.

## Security audit events

`USER_REGISTER`, `USER_LOGIN` (including BLOCKED when throttled), `ADMIN_BOOTSTRAP`, `RATE_LIMIT_BLOCKED`,
`TOKEN_REFRESH` (including BLOCKED on reuse), `USER_LOGOUT`, `ADMIN_ACCOUNT_CREATED`. Writes use `REQUIRES_NEW`.
Voting and credential actions are intentionally **not** audited.
