# 0.1.39 · Lineage 2 appearance

Settings → Additional → Themes offers Standard and Lineage 2 with previews. Standard remains the default. Theme selection persists and applies in place: the MapView, camera and running MonitorService are retained. No new taxi queue command or server schema is introduced.

Lineage 2 uses dark metal panels, bronze/gold borders, native Cyrillic text, parchment/olive real-world cartography, metal point labels, crystal position markers and a sapphire overlay with a fine gold rim. The supplied emblem is reinterpreted as layered Canvas vectors (curved silver S, gold frame/wings, sapphire core, gems, runes and SensePro paths), not an embedded raster claimed to be vector. Fine texture from the bitmap is intentionally simplified for phone rendering. Only internal light rotates; the letter and frame remain upright. Actual running state controls the Pro indicator. Visible active artwork animates at up to 30 fps; hidden/detached views cancel callbacks and system animation disablement is respected.

Lineage 2 has no audio assets in this patch. AppSounds and StartupSound are gated by theme; selecting a theme stops current cues. Existing `sounds` preference is retained for Standard. Point/friend notifications use separate silent channels in Lineage 2, keeping the original channels and settings for Standard. Native system confirmation dialogs are controlled by Android. A future patch will add the Lineage sound set.

Validation scope: release/debug/test APK builds, lint, two targeted API35 AppearanceTest scenarios covering theme selection/restoration/persistence, retained map/data/service state, silent notifications, vector rendering/hit area, widget fit, and screenshots. Full legacy emulator matrix is not required for this presentation-only patch. Results and signing evidence must be recorded after checks finish; this document does not itself claim a successful run.

## Build and signing checkpoint

Local release/debug/test APK builds and lint completed successfully. Lint: no errors. APK versionCode40; package dev.raf.radar; original certificate SHA256 7baf77afc2f4b5c570c9b08d6092f55ad3ec10a2d798649c846bf2a1429bbd89. Signed APK SHA256 9c2b27ce955f7540639040f47d987b55d254e84816e8644bb8258bf2e8819b6e.

Initial API35 theme/overlay scenarios passed in CI40089637696. After inspecting screenshots the emblem was refined and reduced-motion handling corrected. Final code f0679c4c3cf83f3e3552ec39a4523b63489734c0 passed CI40090243201: release/debug/test builds, lint and both API35 AppearanceTest scenarios (including disabled system animations). Final screenshots are reviewed before delivery. No physical Huawei acceptance has been performed in this session.
