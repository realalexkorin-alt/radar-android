# 0.1.24-alpha — faster main widget

- Own location surcharge has a 10-second polling target while the widget is enabled or Map/Points are visible. Network duration and other due points can delay a response.
- Saved points retain the configured interval. Single serialized request queue, deduplication and error/rate-limit backoff remain. Manual refresh cannot bypass the global budget or server pause.
- Focused validation: PollPlanner tests (including 10-second boundary and 15-minute backoff), assembly and lint. No emulator/full suite required for a scheduling-only change.
