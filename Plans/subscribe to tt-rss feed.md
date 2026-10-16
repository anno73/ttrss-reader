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
- Implementation and CI validation in progress. No local tests/builds.
