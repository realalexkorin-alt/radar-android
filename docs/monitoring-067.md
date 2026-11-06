# Sense PRO 0.1.67

Own-location monitoring targets ten seconds, with no accumulated requests. Saved points are eligible only while the map has foreground focus. Saved requests start at least ten seconds apart, fill gaps with a bounded budget (three-second fare request plus up to two seconds for a missing zone lookup), abort when the map hides, and never trigger an automatic session reload. Own requests win when due. Requests remain serialized. Slow initial session loading and server Retry-After can exceed the target interval.

No exponential or cross-point error backoff is applied by the monitor. Explicit Retry-After is respected within Sense; missing/invalid headers use the normal ten-second interval. A one-time migration clears the obsolete local 066 cooldown. Existing authentication checks still stop rejected sessions. WebView interception applies only to Sense's own WebView and only during a live server cooldown; the old post-cooldown interception condition is removed. No global network, VPN, proxy or other application's traffic is controlled.

Observation collection, source synchronization, and queued uploads are suspended, preserving existing history. History views stay read-only. Real-world Yandex Pro orders/routes require owner-device validation; emulator tests do not establish lack of account/IP throttling.
