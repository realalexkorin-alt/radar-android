# Incremental release status — 2026-10-23

GitHub is the source of truth. Each release is signed with the existing certificate; install over the old app without uninstalling. Each patch used focused checks rather than the full legacy emulator matrix.

| Release | Delivered |
|---|---|
| 0.1.22 | Optional bottom-left widget age, default off |
| 0.1.23 | Last-success surcharge age on saved map points and Points list |
| 0.1.24 | Faster priority main-widget polling, server backoff retained |
| 0.1.25 | Peer read timeout fix, successful cache, explicit refresh and coordinate age |
| 0.1.26 | Better GPS fix selection and sync while screen/monitoring runs |
| 0.1.27 | Chosen numeric ID 1–999, password login/change, rotating 16-digit recovery, migration |
| 0.1.28 | Confirmed private friends, requests/badge, indefinite last-position retention and local notifications |
| 0.1.29 | Fair queue, fare bound to request coordinates, coalesced peer refresh and migration identity fix |
| 0.1.30 | Rolling private diagnostic reports, stalled-update watchdog, offline retries, protected server reader and settings cleanup |

| 0.1.31 | Compact profile, clean settings, rounded dialogs and vector friend refresh |
| 0.1.32 | Optional surcharge direction with placeholder0%, single online/offline caption |
| 0.1.33 | Five-minute fixed-point observations, durable account-scoped queue, server weather and private history |
| 0.1.34 | Geographic anchors, local label dragging and kilometer-area glow |
| 0.1.35 | Remove forecast radius glow and label dragging; ignore previous visual offsets |
| 0.1.36 | Round overlay, stable +999 reference size, centered numeric age; widget/font size sliders retained |

## Remaining external dependency
Actual push when app is fully closed: no Huawei Push Kit app configuration is available. SDK/provider transport is not implemented or advertised as complete. See HUAWEI_PUSH_SETUP.md. Local notifications require app/foreground monitoring to sync.

## Manual physical-device acceptance
1. Update both phones to latest APK over existing installation.
2. Register or log in; choose ID 1/2 if desired and free. Save the 16-digit recovery code.
3. Add the other ID, accept the incoming request, enable location sharing.
4. Confirm distinct markers, nickname change, last-seen status, offline retention, refresh and friend removal on two physical phones.
5. Check GPS outdoors and background behavior under each Huawei battery setting. Emulator checks cannot certify device-specific GPS accuracy or OEM process survival.

## Infrastructure
Neon-backed production API verified with disposable synthetic account/location fixtures. Account lifecycle and legacy migration passed; temporary accounts/coordinates removed. Legacy endpoint remains compatible; private account storage has no location TTL. Runtime log connector returned 403 (observability gap); successful API tests do not establish a clean runtime-error history.

## Resource model
One API-35 emulator only for changed native flows; pure scheduler changes use targeted unit checks. No full app suite for cosmetic/isolated patches. One serialized taxi queue with global budget and rate-limit backoff; 10-second main target alone, longer when due saved points share slots. Friend polls coalesced; at most one coordinate publication in flight. No signing passwords, provider credentials or database secrets in public source or APK.

## Diagnostics 0.1.30
Production /api/diagnostics accepts authenticated uploads; read requires a separate private bearer. Last-hour journal and bounded offline queue are persisted. Server retention7d/50 reports per user. `scripts/read-diagnostics.py` derives read access from the existing protected signing backup; see PATCH_0.1.30.md. Live auth/upload/read/filter/dedup/cascade checks passed. Previous server runtime-log connector403 remains; application reports provide a separate verified read path. Reports start after the new APK is installed; cause of the old hour-long stall is still unconfirmed.

## Statistics acceptance
Install0.1.33 or newer on both phones, enable monitoring and5–10 fixed points per phone. Forecast toggle affects display only; it does not enable/disable collection. Actual capture follows successful fresh replies at a target minimum interval5min per point; API pauses and process stops create real gaps. No moving GPS trace is collected. Weather is hourly model data from Open-Meteo, rounded-coordinate cache30min, explicit units and timestamp. Server history90d; weather failure preserves surcharge with NULL weather. Access via scripts/read-statistics.py and the same protected reader derivation as diagnostics. Synthetic tests on production passed auth, zero, dedup, one-kilometer region assignment, weather/time/units and deletion; fixture data removed. Summary is initially empty until phones install the new APK. First-day analysis measures coverage and sampling quality; predictive accuracy is not established.

Release/test/signature details for the completed series: PATCH_SERIES_0.1.31-34.md. Latest versionCode35; the latest APK contains all four incremental changes.

0.1.35 removes only radius visualization and dragging; server statistics/radius grouping remain. Saved points are local; friend point copying is a proposal for a separate catalog/permission/import feature, not shipped.

0.1.36 changes only overlay rendering and its targeted check. Both user scaling controls and opacity remain; numeric age does not affect diameter or price center. Final real-screen rendering check passed onAPI35.
