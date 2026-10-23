# 0.1.26-alpha — GPS quality and active-app sharing

- Active GPS requests every 3 seconds, other enabled providers every 5 seconds. Original measurement age is preserved.
- Prefer recent precise measurements over overlapping inaccurate network fixes for 30 seconds. Reject impossible short-term jumps outside uncertainty circles; accept reacquisition after a 2-minute gap. Check finite coordinates and geographic ranges.
- Serialize foreground/service writes to shared GPS state.
- Explicitly enabled sharing now publishes while the app screen is visible even with PRO off. Foreground monitoring keeps publication in background; leaving without monitoring marks offline. Existing sharing switch remains authoritative.
- GPS accuracy depends on the device and surroundings; no synthetic precision or coordinate displacement.
