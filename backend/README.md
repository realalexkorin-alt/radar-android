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

## Checkpoint, 2026-10-20

Connection to the reused free Neon resource is verified. SQL access to the same compute is verified through a protected diagnostic deployment. pg_cron is available but not enabled for neondb; installation in postgres is denied to the application role. The Neon console requires email activation before compute configuration can continue. No schema installation completed, no real two-client SQL test yet, no production release yet. Temporary test bypass was revoked. Diagnostic deployments remain protected and must be deleted in Vercel before production protection is changed.
