# 0.1.25-alpha — reliable peer loading

Measured API response 9.7s exceeded old Android read timeout 8s. New connect/read 15/25s. Separate serialized reader executor avoids publication queue starvation. Malformed records are isolated. Successful directory cache survives restarts; errors preserve it. Friends has explicit refresh/progress and coordinate age separate from last seen. Public sharing and 7-day retention remain until private accounts.

Validation: workflow 39935841801 passed assembly/lint/formatter and 2 Android tests: real HTTPS two synthetic clients (publish, query, offline retention, cleanup) and cached-directory recreation/error/refresh UI. First run exposed test fixture refresh interference, isolated in test only; repeat passed.
