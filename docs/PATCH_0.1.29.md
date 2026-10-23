# 0.1.29-alpha — fair polling and coordinate-bound surcharge

Final integration review found two related data issues:
- A continuously due 10-second main widget could starve saved points. Due saved points now receive alternating slots; main alone still targets 10 seconds, mixed queues can take 20 seconds for main. Server backoff/global limits remain.
- Gradual movement could carry an old fare over many small GPS steps. Bind own-location fare to the coordinates used for its request, invalidate after accumulated movement beyond 250 m, expedite a new fetch. Keep last-success timestamp for the optional age caption. No invented surcharge when data is unavailable.

Focused validation: PollPlanner fairness/budget/backoff tests; native gradual-movement test with real Android Location distances, cache invalidation and zero-ruble success.

- Coalesce simultaneous friend refreshes from map, dialog and background sync into one request; keep only one location publication in flight. This bounds queue growth during slow network responses. Native test verifies three concurrent subscribers receive one network result.

- Whitelist migrated coordinate fields so a legacy installation hash cannot overwrite the private numeric account ID in friend responses. Registration records an initial last-seen timestamp. Verified against a synthetic legacy publication migrated into an account.
