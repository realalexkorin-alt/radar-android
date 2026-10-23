# 0.1.28-alpha — confirmed private friends

Plus/add by ID, incoming accept/reject, outgoing cancel, long-press removal. Server validates both parties and prevents self/duplicate edges. Pending-request badge. Only accepted friends appear on map/list; accounts without a saved coordinate stay in list without a fabricated marker.

Last position retained indefinitely for private accounts, including sharing/monitoring off. Explicit delete-last-position in settings. Removing a friend revokes API read access and removes coordinates from successful client state. Local offline cache and manual refresh retained.

Local request notifications on successful app/service sync open Friends. These are not server push: delivery with app fully closed requires Huawei Push Kit app configuration; see docs/HUAWEI_PUSH_SETUP.md.

Validation: workflow 39938404756 passed backend auth tests, release assembly, lint, formatter and API-35 FriendsUiTest plus AccountsNativeTest (two synthetic real Android account clients, request/accept/private position/offline retention/removal/cleanup). Source cd3c79fef194225fbecc0d4cde614a06a81196b9. Earlier compile failure was an ambiguous static test import, corrected without production code changes.
