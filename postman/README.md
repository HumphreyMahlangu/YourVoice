# YourVoice Postman Assets

Import these two verified files into Postman:

* `YourVoice.verified.postman_collection.json`
* `YourVoice.verified-local.postman_environment.json`

Select the `YourVoice Verified Local` environment before running requests.

## Before You Start

Start the API locally:

```powershell
docker compose up --build
```

The local environment assumes:

* `baseUrl`: `http://localhost:8080`
* `adminBootstrapToken`: same value as `YOURVOICE_ADMIN_BOOTSTRAP_TOKEN` in `.env`
* `adminPassword` and `voterPassword`: `VeryStrongPassword1`

## Recommended Flow

1. Run `00 - Start Here / Reset Verified Demo Data`.
2. Run `01 - Admin Auth / Bootstrap First Admin - Optional` once. If an admin already exists, continue with `Login Admin`.
3. Run `01 - Admin Auth / Login Admin`.
4. Run every request in `02 - Election Setup`. The timing check pauses the flow until automatic registration opening.
5. Run every request in `03 - Public Reads`.
6. Run every request in `04 - Voter Registration` before `registrationEndAt`.
7. Run `05 - Voting` in order. If a timing or status check tells you to wait, allow the lifecycle scheduler a few seconds and rerun that request. `Get Contest Ballot Key` stores the contest's RSA key; `Issue Voting Credential` blinds a random ballot token in its pre-request script, sends only the blinded value, and unblinds and verifies the returned signature in its test script. `Cast Ballot` then sends the token and signature without the voter's JWT.
8. Run `06 - Results and Audit` in order. The collection confirms automatic completion before requesting results.
9. Run `07 - Negative Checks` only after a successful ballot has been cast.

The verified collection generates unique demo data on each reset. By default, ballot configuration has 120 seconds before registration starts, registration stays open for 180 seconds, and voting stays open for 90 seconds. You can change `configurationGraceSeconds`, `registrationGraceSeconds`, and `votingDurationSeconds` before running `Create Election`.

Election and contest statuses advance automatically from their configured UTC timestamps. Administrators do not manually open registration, open voting, or complete an election.

The older `YourVoice.postman_collection.json` files are kept for compatibility, but use the verified pair above for manual testing.
