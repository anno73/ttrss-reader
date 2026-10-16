# Subscribe to a tt-rss feed

## Plan and scope

- Create `feat/ttrss-subscribe-feed` from upstream master.
- Restore skill tooling and placeholder templates from the existing skill branch,
  without restoring tracked private configuration or its history.
- Add explicit SubscribeFeed with HTTPS URL validation, mutation guard,
  uncategorized default, and correct nested/legacy subscription result handling.
- Extend mocked PowerShell contract tests and restore their Android CI job.
- Push, verify CI and download ZIP/both APKs in the required run-specific layout.
- Once verified, subscribe profile test to https://petapixel.com/feed/ as requested
  and verify the subscription through feed listing. Never replay on uncertainty.
- Record outcomes, commit and push; do not merge without approval.

## Progress and validation

- Restored the existing skill and public templates; no local config or dotenv
  content was read or changed.
- Verified upstream subscribeToFeed request parameters and Feeds::_subscribe
  comments: status 0 already exists, 1 added, 2..8 explicit failures.
- Implemented SubscribeFeed with a mandatory mutation guard, URL/category
  validation, explicit success/already-subscribed outcomes, and safe errors for
  all nested/legacy result codes. No automatic subscription retries.
- CI run [38935903375](https://github.com/anno73/ttrss-reader/actions/runs/38935903375)
  passed for code commit `54f075ce`: 123 PowerShell contract tests passed,
  zero failed, and Android tests/builds/identity/signing checks succeeded.
  No local tests/builds were run.
- Downloaded `downloaded-apk/run-38935903375/ttrss-reader-debug.zip` and verified
  SHA-256 `d27a0824fbd831ec1b28f7cd7f10d2b9ff55473244d1fb86d7025ce0bbd609ca`
  against the artifact digest. Extracted directly into that directory and
  verified both `ttrssreader/build/outputs/apk/fdroid/debug/ttrssreader-fdroid-debug.apk`
  and `ttrssreader/build/outputs/apk/play/debug/ttrssreader-play-debug.apk`.
- Authorized live execution succeeded on profile test: subscribed to
  https://petapixel.com/feed/ with feed ID 317, category 0 (uncategorized).
  A separate GetFeeds call confirmed title PetaPixel, the exact feed URL and
  the returned ID. Both invocations completed logout successfully.
- Real credentials and backend configuration remained local and untouched.
- User confirmed that the UI/manual check passed, then explicitly approved
  merging. Merged and pushed to master as `cb02eb09`.
- Post-merge workflow
  [38936370335](https://github.com/anno73/ttrss-reader/actions/runs/38936370335)
  succeeded for both jobs. Downloaded its ZIP into
  `downloaded-apk/run-38936370335/`, verified digest
  `ae4607e6f7e8c49fe8754f5ddef90869de7c01d015204e160d012ddfca3ce044`,
  and verified both extracted APKs in the same required layout.
- Final plan-only commit uses `[skip ci]`; executable code is identical to the
  verified post-merge run. Work completed; local configuration remains ignored.
