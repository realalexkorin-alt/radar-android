# Radar presence service

Vercel Node.js Function + a dedicated Redis database. No accounts, names, tracks or passenger data.

Required server environment: `UPSTASH_REDIS_REST_URL` and `UPSTASH_REDIS_REST_TOKEN` (the `KV_REST_API_*` names from the Vercel integration are also supported). These values must never be put into Android resources or source control.

`GET /api/presence?west=60&south=54&east=63&north=57` returns currently fresh public points in the viewport. PUT and DELETE require `Authorization: Bearer <random 32-byte token as lowercase hex>`. The public ID is SHA-256 of that token. Every write includes an increasing integer `revision` to prevent reordering from restoring a withdrawn location.

A PUT carries `lat`, `lon`, `accuracy` in metres, and `observedAt` (Unix milliseconds). Locations expire within 120 seconds of their original observation, not the HTTP request. Each point has a Redis TTL; the index contains hashes and observation times only. Revision hashes expire after one day and rate-limit counters after one minute.

Run `node --test test/*.test.js` for API contract tests. They use an in-memory command adapter; they do not prove Redis Lua integration. Before publication, run `test/live.mjs` with `RADAR_TEST_ENDPOINT` set to a staging endpoint connected to the dedicated database. It creates two temporary synthetic points, checks visibility, isolation, withdrawal and replay protection, and removes them in a finally block. Never use live personal locations for tests.

A deployment whose database is missing must return 503. Production must expose the API over HTTPS without Vercel login; preview deployments may remain protected. Use separate storage for a large public rollout. Current response cap: 200 visible points among the 1000 most recently active installations.
