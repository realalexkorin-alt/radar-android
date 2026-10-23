# 0.1.28-alpha — confirmed private friends

- Friends: plus/add by ID, incoming accept/reject, outgoing cancel, long-press removal. Prevent self/duplicate requests; mutations checked by server. Request badge counts pending incoming requests.
- Only accepted friends appear on map/list. Accounts with no coordinates stay in list; centering waits for a real coordinate. Names refresh from server.
- Last shared position persists indefinitely on server and device while friendship remains. Sharing off/monitoring off does not delete it. Settings includes explicit last-position deletion. Removing friend revokes server read access and clears coordinates from successful client state.
- Manual refresh progress, cached data on connection failure, background sync while app/monitoring runs.
- Local request notifications on sync open Friends on map. Actual push delivery with application fully closed still requires Huawei Push Kit application setup/credentials; polling notifications are not represented as server push.
- Validation: backend auth/friend ACL/live lifecycle checks, native two-account private position exchange and Friends UI/badge test on API 35.
