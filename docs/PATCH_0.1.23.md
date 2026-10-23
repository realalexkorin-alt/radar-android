# 0.1.23-alpha — update age on saved points

- Saved map markers and Points rows show the last successful surcharge update, in seconds/minutes/hours/days. Small white text at 70% opacity.
- Unknown: «Нет обновлений». Failed responses retain the last successful timestamp.
- Existing visible-screen refresh updates the age every 5 seconds, with no added network requests. Own GPS marker stays circular.
- Focused checks: formatter boundaries, point label persistence after error, marker layout and Points row rendering on API 35.

Next: priority widget polling, peer load repair, GPS/background sync, accounts, confirmed friends and push.
