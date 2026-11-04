# Feed list error context

## Problem

Updating the feed list could fail with only `getFeeds: could not read page (IOException)`. The message did not say which page, feed, or request failed.

## Work completed

- `JSONConnector.getFeeds` now reports page failures with:
  - the exception class and message (for example `Invalid feed record` or a JSON location),
  - the request parameters `op=getFeeds`, `cat_id`, `offset` and `limit`,
  - the API URL, with user and password redacted,
  - the last feed parsed before the failure (id, title and feed URL, with user and password redacted).
- Timeout messages were already detailed and are unchanged.
- `getHeadlines` still uses the short `could not read page` message. It is a possible follow-up.
- Added `ApiPaginationTest.feedPageFailureReportsPageAndRedactedLastFeed`, which checks the page context and credential redaction.

## Validation

- Local Gradle, build and test runs were not executed, per repository policy.
- GitHub Actions run `41894006702` on `fix/feed-list-error-context` (commit `3c41c68f`) succeeded. Its test step runs `org.ttrssreader.net.*Test`, which includes the new test.
- Artifact `ttrss-reader-debug` was downloaded to `downloaded-apk/run-41894006702/` with the required layout.
- Manual test on the phone is pending. The merge needs the user's approval after manual testing.

## Limitations

- The feed URL keeps its query string. Only user info is redacted, so a token passed in the query would still appear.
- The last parsed feed is the feed before the failing record, not the record itself, because the failing record could not be parsed.
