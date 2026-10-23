# 0.1.29-alpha — data freshness and fair queue

Continuously due main-widget polling no longer starves saved points. Main alone targets 10 seconds; mixed due queues can take 20 seconds for main. Global budget/server backoff remain.

Bind own-location surcharge to request coordinates; invalidate after accumulated movement beyond 250m and expedite next fetch. Last-success age is preserved. Coalesce concurrent map/dialog/background friend refresh into one request and bound in-flight location publication to one.

Whitelist migrated coordinate fields so the old installation hash cannot overwrite numeric private-account identity. Registration records initial last-seen time.

Validation: workflow 39939674831 passed backend auth tests, assembly/lint, PollPlanner fairness/budget/backoff unit suite and 2 Android API-35 tests (gradual GPS movement/fare invalidation/zero success; 3 concurrent refresh subscribers with one request). Source 3d45de9b6e0b34be1e3fe9a666f0c9bbea9d2aa7. Artifact 12494962959 SHA-256 b1b21a3bc70e201376ac470e11a1695a0a1825f5446a6bffa25e440e126a6ebf.

Production backend dpl_HoVTaYp1yoYAwBQQXoqEeMTVXfSk READY. Live legacy-to-private migration verified numeric ID/coordinates, public withdrawal, name propagation and explicit point deletion preserving friendship. Temporary account/coordinate fixtures removed. Physical two-phone acceptance remains manual. True Huawei push requires external app configuration; see HUAWEI_PUSH_SETUP.md.
