# Radar presence service

Vercel Node.js Function with the existing Avito Neon PostgreSQL database, reused by the owner's instruction on 2026-10-20. No new subscription was created. Android never receives database credentials.

## Provisioning

Connect `neon-amethyst-blanket` to `radar-presence`, Production and Preview, injecting sensitive `DATABASE_URL`. In Neon, enable `cron.database_name=neondb` for the existing compute, then apply `schema.sql` as the database owner. Neon requires compute configuration before pg_cron can be installed: https://neon.com/docs/extensions/pg_cron . Merely seeing pg_cron in pg_available_extensions is not enough.

The isolated `radar_presence` schema contains current points, hashed installation revisions and hashed rate counters. Avito tables are not dropped or changed. The original Redis adapter is retained as a fallback for installations without DATABASE_URL; PostgreSQL is selected whenever DATABASE_URL is set.

## API and privacy

`GET /api/presence?west=60&south=54&east=63&north=57` returns fresh public points in the viewport. PUT and DELETE require `Authorization: Bearer <random 32-byte token as lowercase hex>`. Public ID = SHA-256 of that token. Increasing integer `revision` prevents old requests from restoring a withdrawn point. PUT carries lat, lon, accuracy in metres and observedAt in Unix milliseconds. No user names, profiles or movement history.

PostgreSQL expires coordinates after 110 seconds from observation (never later than 110 seconds from server receipt) and runs cleanup every 10 seconds while points exist. This leaves a scheduling margin inside the agreed 120-second maximum. DELETE clears coordinates immediately. Cleanup stops its own cron job when no points remain, allowing the compute to sleep. Writes and cleanup use the same transaction advisory lock, and revision checks are atomic. Metadata has logical expiry of 60 seconds / 24 hours and is purged by subsequent cleanup; it contains no coordinates. SQL binds user values as parameters and does not log them.

**Release gate:** verify actual pg_cron execution and coordinate removal without API traffic before publishing. If the compute or scheduler is unavailable, expiry predicates still prevent serving stale points, but physical cleanup must not be claimed successful. Do not disable scale-to-zero or change billing without approval.

## Validation

`npm ci --ignore-scripts && node --test test/*.test.js` runs API contract tests using an in-memory storage adapter. It does not prove PostgreSQL functions or scheduling. Run `test/live.mjs` with RADAR_TEST_ENDPOINT against a protected preview with real Neon storage. It creates two synthetic identities, checks visibility, viewport, isolation, withdrawal and replay protection, then withdraws both in finally. Additionally seed an expiry fixture, leave it untouched for 120 seconds and verify point=NULL plus automatic job shutdown directly in SQL.

Missing or broken storage returns 503, never fake points. All API responses forbid caching. Production must expose the API over HTTPS without Vercel login; previews remain protected. Response cap: 200 visible points among the 1000 latest active installations; add spatial indexing before a large rollout.

## Validation checkpoint, 2026-10-20

The existing free Neon database is connected. Email activation is complete; cron.database_name=neondb is configured, pg_cron 1.6 and schema.sql are installed. Four API contract tests pass. The real PostgreSQL test passes for two clients, viewport selection, isolated withdrawal, replay rejection, automatic coordinate removal after 121 seconds with no API/SQL traffic and shutdown of the empty scheduler. HTTP preview validation passed. Production API responds 200 with no-store on https://radar-presence.vercel.app/api/presence. Production deployment: dpl_E28b5yNrVSeskPQvAw2ExkRAvdKN, region iad1. Temporary project-scoped Neon API key was revoked and a follow-up request returned 401. Temporary Vercel test bypass was also revoked.

The live schema has no movement-history table. Neon infrastructure point-in-time recovery currently has its own retention policy (6 hours observed); working-table expiry is not a promise of erasing infrastructure recovery versions. Do not claim otherwise. Diagnostic previews remain protected; production should be public while Vercel protection stays enabled for previews.

## Resource budget

Reuse the existing Free Neon project; no new subscription. Android sends/polls presence every 30 seconds, only reads peers on the visible map, and only publishes with PRO + explicit consent + fresh GPS. SQL cleanup runs every 10 seconds only while coordinates exist and removes its schedule when empty; compute scale-to-zero stays at five minutes. API responses are capped at 200 points and queries inspect at most 1000 active installations. The pre-existing surcharge request queue and backoff remain in force. Re-run the real SQL/expiry test only after storage or scheduler changes; interface and Android upgrade checks are required when Android behavior changes.

## Observation history retention

Observation history is retained indefinitely (no age-based expiry). The hourly
`radar_presence.cleanup_statistics()` task deletes only expired weather-cache
entries and unused region records; it never deletes observations. Weather already
stored with an observation stays with that observation. The statistics API returns
`retentionDays: null` to indicate no time limit.

On deployment, call the authenticated statistics read endpoint to initialize the
updated cleanup function in the database, then verify the live retention policy.
This changes observation retention only; diagnostic logs and presence TTLs keep
their existing policies. Explicit account deletion still follows the existing
foreign-key cascade.
