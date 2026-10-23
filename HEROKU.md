# Deploy YourVoice on Heroku

This deploys the Spring Boot API and React frontend together on one Heroku web dyno. YourVoice is a portfolio voting simulation, not a certified election platform.

## Before creating paid resources

1. In your **personal** Heroku account, open **Account Settings → Billing → Platform Credits** and confirm the GitHub Student offer is active. The $312 allocation provides **up to $13 each month for 24 months**. Heroku deducts that month's $13 allocation even if you use less, and unused credit does not carry forward. Heroku requires a payment card and charges usage above that month's credit. The credit does not cover paid third-party add-ons.
2. Check **Billing → Usage** and **Apps / Resources** for other running resources. The suggested setup is one Basic web dyno ($7/month) plus one Heroku Postgres Essential-0 database ($5/month). Together they are about $12/month while those prices remain current. Other resources in the account share the $13 monthly credit. Basic stays awake so the election lifecycle scheduler can run.
3. If you already have data in Render's `votetrust-db`, this procedure creates a **new, empty database**. Plan a separate, verified migration if that data must be retained. Keep the Render deployment until the Heroku app has been checked.

## 1. Create the Heroku app

1. Commit and push `system.properties` to the GitHub branch you will deploy. It selects Java 21, which this project's Maven configuration requires.
2. In the [Heroku dashboard](https://dashboard.heroku.com/apps), create a new app in your **personal account**. Choose a unique name (for example, `yourvoice-api-<your-suffix>`) and a nearby region. If offered a stack choice, use `heroku-26`.
3. On **Deploy**, select **GitHub**, authorize GitHub if prompted, connect `HumphreyMahlangu/YourVoice`, and select the branch with `system.properties`. Leave automatic deployment off until the first deployment works.
4. On **Resources → Add-ons**, add **Heroku Postgres**, choosing exactly **Essential-0**. Confirm the displayed price before provisioning. Wait until it says available. The add-on creates `DATABASE_URL` automatically; Heroku's Java buildpack converts it into `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` at dyno startup. Do not copy database credentials into GitHub or set a static datasource URL yourself.
5. After the first deployment creates a `web` process, check **Resources** and make sure exactly **one Basic** web dyno is running. Do not subscribe to Eco or select a higher-cost dyno type for this setup.

No Redis, mail, file-storage service, or third-party add-on is needed by the current code. Flyway creates the database schema when the API starts.

## 2. Set private configuration

In **Settings → Config Vars**, add these three values as **independent random strings of at least 32 characters**:

| Key | Value |
| --- | --- |
| `YOURVOICE_JWT_SECRET` | Random secret #1 |
| `YOURVOICE_ID_HASH_PEPPER` | Random secret #2 |
| `YOURVOICE_VOTE_CREDENTIAL_PEPPER` | Random secret #3 |

Generate each value separately on your own computer, for example by running `openssl rand -base64 48` three times. Paste them only into Heroku Config Vars or a private password manager. Never commit them, paste them into a public issue, or send them in chat. Back up both peppers securely: changing either after users register can break identity matching or active ballot credentials.

Also add:

| Key | Value |
| --- | --- |
| `YOURVOICE_CORS_ALLOWED_ORIGINS` | The exact HTTPS origin of your Vercel frontend, such as `https://your-voice-delta.vercel.app` (no trailing slash) |
| `YOURVOICE_ADMIN_BOOTSTRAP_ENABLED` | `false` |
| `BUILD_FRONTEND` | `true` |

`BUILD_FRONTEND=true` tells the Maven build to compile the React app and package it inside the Spring Boot JAR. Leave the Vercel origin in `YOURVOICE_CORS_ALLOWED_ORIGINS` only if you still use that separate frontend. The Heroku-hosted frontend calls the API on the same origin, so it needs no CORS setting of its own. Do not set `VITE_API_BASE_URL` on Heroku; if it is already set, remove it so the frontend uses its own origin. Do not add `PORT`; Heroku sets it. Leave the database values managed by Heroku.

## 3. Deploy and check the app

1. Commit and push the frontend packaging changes in this repository. On **Deploy**, use **Deploy Branch**. The Java buildpack detects the root `pom.xml`; with `BUILD_FRONTEND=true`, Maven runs `npm ci` and `npm run build`, then includes `frontend/dist` in the Spring Boot JAR. The existing `web` process serves both the UI and API on Heroku's `PORT`.
2. Watch **Activity → Build Log** and **More → View logs**. The first startup applies Flyway migrations. If startup fails, check that Postgres is available and all three required secrets are present.
3. Open `https://<your-heroku-host>/actuator/health/readiness`. It should return JSON with `"status":"UP"`. Open `/` for the React homepage, and refresh `/elections` to check that direct links work. `/swagger-ui.html` still shows the API documentation.

If startup logs show `UnknownHostException` for a `dpg-...` hostname, the app is still pointed at a Render-internal Postgres address. In Heroku **Settings → Config Vars**, remove stale manually set `SPRING_DATASOURCE_URL` or `SPRING_DATASOURCE_HOST` values; keep the Heroku Postgres-managed `DATABASE_URL`. The Java buildpack derives the Spring datasource connection from `DATABASE_URL` when the dyno starts. Never paste database URLs or passwords into a support conversation.

## 4. Optional: keep the Vercel frontend

In the existing Vercel frontend project, set `VITE_API_BASE_URL` for **Production** to the Heroku API origin, for example `https://<your-heroku-host>` (no trailing slash), then redeploy the frontend. Vite reads this value at build time, so changing it requires a new frontend build. Make sure `YOURVOICE_CORS_ALLOWED_ORIGINS` on Heroku exactly matches the live Vercel origin. Test a browser API call from the frontend.

## 5. Create the first admin only when needed

The first-admin endpoint is disabled by default. To create the initial admin, set `YOURVOICE_ADMIN_BOOTSTRAP_ENABLED=true` and a separate random `YOURVOICE_ADMIN_BOOTSTRAP_TOKEN` (at least 32 characters) in Heroku Config Vars. Call `POST /api/v1/admin/bootstrap` using the `X-YourVoice-Bootstrap-Token` header and the request body described in Swagger. As soon as it succeeds, set `YOURVOICE_ADMIN_BOOTSTRAP_ENABLED=false` and remove the bootstrap token. Do not leave this endpoint enabled. Further admins can be created from the application.

## Costs and runtime behavior

- Basic dynos stay awake, allowing the election lifecycle scheduler to run. The Essential-0 database and one Basic dyno are still portfolio-level services and do not make the app suitable for binding elections.
- The Basic dyno is billed while running; the Essential-0 database is billed while provisioned. Review **Billing → Usage** after deployment and set any available budget alerts.
- Before the student offer expires, remove or scale down resources you do not want billed. Deleting the database deletes its data, so export anything needed first.

Official references: [student credit terms](https://www.heroku.com/github-students/), [Java buildpack and Java versions](https://devcenter.heroku.com/articles/java-support), [Spring Boot and Heroku Postgres](https://devcenter.heroku.com/articles/deploying-spring-boot-apps-to-heroku), [dyno sizes and sleeping](https://devcenter.heroku.com/articles/dyno-sizes), [Heroku usage and billing](https://devcenter.heroku.com/articles/usage-and-billing).
