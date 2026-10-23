# 0.1.24-alpha — faster main widget

Own GPS surcharge targets 10-second polling while overlay is enabled or Map/Points visible. Network duration/other due points can delay responses. Saved points retain their configured interval. One serialized queue and server/error backoff remain; manual refresh cannot bypass the global budget.

Focused workflow 39934755705 passed: release assembly, lint and PollPlannerTest, including 10-second boundary and 15-minute backoff. Source fcceca5d1e621c60b20ca8ddc89648a881f2b0a3. No emulator/full suite for scheduler-only change.
