# 0.1.22-alpha — optional widget update time

The first small release from the agreed patch backlog.

- Widget settings: "Время обновления", default off.
- Small 50% white text anchored at the widget's bottom left, with padding. Widget scale applies; price font size does not determine its position.
- Displays age of the last successfully received Economy surcharge, in seconds/minutes/hours/days. No successful data: "Нет обновлений".
- Persist last successful timestamp independently of current error/stale/GPS display. Zero-ruble results count as successful; failed responses do not reset the timestamp.
- One local timer per visible enabled overlay; stops when disabled, closed or screen off. Screen-on refreshes the actual age. No network requests added and no polling interval change.

Focused checks: ReadingAge unit boundaries and WidgetAgeTest for default-off, bottom-left layout, toggle, one-second age progression, failed response preservation and overlay recreation. Release assembly/lint. Existing profiles/friends/GPS code not changed in this release.

## Remaining releases
1. Age of surcharge data on map and Points list.
2. Prioritized faster main-widget polling, within API rate limits.
3. Repair friend-directory/peer visibility loading with real two-client verification.
4. GPS freshness/accuracy and background sync improvements.
5. Accounts: chosen/free ID 1–999, password, change password, 16-digit recovery code, migration.
6. Confirmed friendship by ID, request handling, private persistent last locations, refresh button.
7. Push notifications, friend-request badge and Huawei delivery.
