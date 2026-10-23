# 0.1.25-alpha — reliable peer loading

- API response measured at 9.7 seconds; previous Android read timeout was 8 seconds. Connect/read timeouts now 15/25 seconds.
- Reads use a separate serialized executor from publication/withdrawal writes, avoiding queue starvation.
- A malformed peer does not fail the complete response. Last successful directory is cached across Activity/process restarts. Errors preserve data.
- Friends refresh control stays open and displays an in-progress state; repeated refresh does not enqueue duplicates.
- Map and friends rows display the age of the actual coordinate observation separately from online/last-seen status.
- Existing public sharing and 7-day retention contract remain until private accounts/friendships are introduced.

Validation: focused Android two-synthetic-client HTTPS publish/query/withdraw test and cached-peer UI test.
