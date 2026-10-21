# 0.1.21-alpha — profiles and SENSE mechanism

## Android
- SENSE is the default logo for every installation, independent of the old experimental preference. The setting is hidden; classic artwork/control code remains as a load-failure fallback. 0.1.20 size and top-left margins retained.
- Intact S moves slightly forward then upward behind the fixed housing. Two backing doors recess and slide apart. Locator reveals; sweep accelerates, with an integrated angle through reversals. Forward timeline matches monitor_ready.ogg (1.98525 s); reverse 1.5 s. No hidden-screen frame loop or startup replay on returning.
- Existing foreground service starts immediately. Its single successful-start cue is queued after current commands, canceled for obsolete starts and stopped with monitoring. No second service or second sound.
- PRO tint and its warm raster highlights are separate layers, fading together to the same green. Neutral metal and SENSE lettering retained.
- Installation ID reuses SHA-256 of the existing random credential, persists across name changes/app restarts. Reinstall/app data reset creates a new installation identity. First entry asks for a nonempty name (max 32 characters); settings expose name editing and selectable ID.
- Map name labels: 10 dp white at 70% opacity. Offline public positions gray with elapsed last-seen text. Friends button below locate lists users sharing a position, taps center the map. No friend relationships/filtering yet; list capped at 200 by the service.
- Large place_labels layer hidden in both map styles; street labels and all existing geometries kept. No fabricated house-number data added.
- Keep-screen-on switch defaults off; sets a window flag only while activity is resumed, cleared on pause.

## Backend and privacy
New /api/members is additive. /api/presence remains compatible with older apps and retains its old 120-second expiry. New map responses merge still-live legacy clients with fallback ID labels. New Android withdraws its legacy publication once to prevent a duplicate.

Names and ID/revision metadata live in radar_presence.profiles_v2. One last shared position per identity lives in members_v2; logical retention 7 days. Online = heartbeat less than 120 seconds old and no explicit stop. Clean stop marks offline immediately; shutdown does not erase an opted-in last point. Turning sharing off deletes public coordinates, with a pending deletion retried while the monitor runs or on reopening. Offline deletion needs connectivity; UI states this explicitly. Profile rename updates names through a join, without changing identity or retaining movement history.

Additive migration initializes lazily in the authenticated server runtime, under a transaction advisory lock. Coordinates and names are SQL parameters; no credentials shipped to Android. Writes/deletes/revisions are atomic. A 10-minute pg_cron cleanup drops expired last positions and unschedules when empty; retrieval excludes expired rows immediately. Metadata contains no coordinates and remains for identity/replay protection. Infrastructure backups retain their provider policy. Existing Avito tables, legacy tables and scheduler unchanged.

## Verification
Focused workflow: backend contract tests, SenseMotion unit tests, Android release/debug assembly and lint, two UI scenarios on API 30 and 35. UI scenarios cover registration, stable identity, settings/window lifecycle, default logo, central control, quick cancel, reverse/stop, background/return, offline/online list and centering. Captured phase images are deterministic renderer samples, not a high-frame-rate animation recording.
Live HTTPS/Neon check uses two random identities in a tiny offshore bounding box: create, rename, stable ID, online/offline, isolated withdrawal and replay rejection; fixture coordinates removed in finally. No broad production location query. Seven-day physical cleanup has not been time-accelerated or claimed observed; query expiry and scheduling implementation are documented separately.

Manual acceptance: judge motion/metal layers and sound synchronization on the physical phone; verify name updates between two real devices and house-number availability at high zoom.

## Release checkpoint
- Tested Android source: 1d206b9ed8469dc1674f20c9fdca0b9f65366b94.
- Focused workflow: 39590908992. API 30 build dependency download initially failed transiently (Groovy/httpclient Maven resolution); only that job was retried. API 35 completed successfully on the final source.
- Final API 35 artifact: 12341781730, ZIP SHA-256 20ae4cc495fa111a834ddfac749970035d45a19e8ced4236ee3ad21b879649e3.
- APK versionCode 22, versionName 0.1.21-alpha. SHA-256 e0bcaef820ad8098c742c550dc2ebc4770a4513b6d836ae04e3dbf58c48763a3.
- Signing certificate SHA-256 7baf77afc2f4b5c570c9b08d6092f55ad3ec10a2d798649c846bf2a1429bbd89 (unchanged).
- Backend production dpl_DhjHeTKx5SgHpPeRAstsTA7gELTk is READY. Live HTTPS/Neon synthetic lifecycle check passed; subsequent scoped query confirmed fixture coordinates removed.
- Protected preview could be deployed, but the connection could not authorize its protected URL. No protection setting was weakened; verification used the additive production API while legacy /api/presence remained unchanged.

Final focused workflow attempt 2 completed successfully on API 30 and API 35; API 30 log confirms OK (2 tests). Six backend contracts and four SenseMotion cases passed. No production implementation changed after the tested source.
