# API timeout messages with request and feed details

## Problem and approach

Backend API timeout errors currently lack the called URL and request body. Extend their existing user-visible error text and diagnostic logging with the request's API endpoint URL and sanitized JSON POST body, captured when the request is built rather than reread from mutable settings.

Scope is backend API requests only. Image caching, feed icons, article resources, timeout durations, retries, and non-timeout errors remain unchanged.

Preserve the exception details and existing dialog flow through `hasLastError` / `pullLastError`. Cover both execution timeouts and delayed response-body read timeouts; `doRequest()` currently returns a lazy Reader, so changing only its catch block would miss the latter.

For timeout requests that target a specific feed, also identify the feed by its locally stored name and source URL. Resolve the `feed_id` from the captured request body against the existing local feed record (`DBHelper.getFeed(id)`); do not issue another API request or depend on mutable global request state. Only attach feed metadata when the operation targets an actual feed (not a category) and a matching feed record is available. If it is unavailable, retain the timeout details without feed metadata. Sanitize credentials and known token query values in the stored feed URL with the same URL sanitizer used for request diagnostics.

## API-reference verification

Source: [current official Tiny Tiny RSS API reference](https://tt-rss.org/docs/API-Reference.html), retrieved during this planning session and compared with all active request builders in `JSONConnector.java`.

- Getting started documents JSON POST requests, the operation selector `op`, and the session-authentication parameter `sid`. API arguments are not normally URL query parameters.
- `login` documents `user` and `password`, returning a session ID. Single-user mode still logs in but can omit both credentials.
- `subscribeToFeed` additionally documents optional feed-authentication `login` and `password`. This client currently sends neither.
- The reference does not document separate `api_key`, `api_token`, or OAuth tokens for these calls. Such names may still occur as credentials in a configured endpoint URL; support them defensively without claiming that they are tt-rss API parameters.
- All 12 active operation names in this client are documented. `updateArticle` has four active variants, listed separately below.
- `isLoggedIn` is a commented-out constant, not an active request. `logout`, `getApiLevel`, `getConfig`, `getLabels`, and other documented operations are not independently called by this connector. Login response parsing reads configuration/API level without making those calls.

## Confirmed diagnostic policy

- Replace embedded username/password and known credential/token query values with the exact text `VALUE_REDACTED`.
- Preserve non-sensitive query values and query parameter names; do not redact all query values.
- Redact only credentials and authentication/session tokens. Preserve all other API parameters and their values, including search text, article IDs, notes, titles, content, and ordinary URLs.
- Match credential/token parameter names case-insensitively, normalizing hyphens to underscores. Known names: `user`, `username`, `login`, `password`, `passwd`, `pwd`, `token`, `api_token`, `api_key`, `apikey`, `access_token`, `refresh_token`, `auth`, `authorization`, `sid`, `session_id`, `secret`, `client_secret`. `op: "login"` is an operation selector, not a credential; keep it unchanged.
- Use OkHttp URL parsing/building to safely encode the diagnostic URL. Never change the URL or JSON sent to the server. Never emit authorization headers or HTTP Basic-auth credentials.
- Report the API request endpoint URL, not the resolved socket/IP address. Preserve scheme, host, port, path, and non-sensitive parameters.
- Include the sanitized JSON body in both the timeout dialog message and timeout diagnostic log, with clear `URL:` and `JSON:` labels. Redacted fields remain present with value `VALUE_REDACTED`; do not remove them.
- For feed-targeting operations, include the resolved feed name and sanitized source URL in both the timeout dialog message and diagnostic log. The source URL is the subscribed feed URL, distinct from the API endpoint URL shown under `URL:`. Do not add feed metadata for category-targeting requests or when the feed is not in the local database.
- For structured URL values in `feed_url` and `url`, retain the URL but replace embedded credentials and known credential/token query values using the same URL sanitizer. Do not redact the entire URL parameter.
- Do not alter free-text fields or parse arbitrary secrets out of `search`, `data`, `title`, or `content`. These remain visible by explicit request. Absent fields remain absent; do not synthesize credentials or session fields.

## Per-call JSON visibility and redaction

In every row, retain `op` and its original value. The connector injects `sid` whenever its session ID is non-null, including potentially a login request. Whenever present, `sid` becomes `"VALUE_REDACTED"`. The listed retained fields are those actually sent by this client, not every optional field documented upstream.

| API operation / client variant | JSON parameters replaced with `VALUE_REDACTED` | JSON parameters kept unchanged |
|---|---|---|
| `login` | `user`, `password`, `sid` if present | `op` |
| `getCategories` | `sid` | `op` |
| `getFeeds` | `sid` | `op`, `cat_id` |
| `getHeadlines` | `sid` | `op`, `feed_id`, `limit`, `skip`, `view_mode`, `is_cat`, `show_content`, `include_attachments`, optional `since_id`, optional `search` |
| `updateFeed` (lazy-server update) | `sid` | `op`, `feed_id` |
| `updateArticle` (unread, `field=2`) | `sid` | `op`, `article_ids`, `mode`, `field` |
| `updateArticle` (starred, `field=0`) | `sid` | `op`, `article_ids`, `mode`, `field` |
| `updateArticle` (published, `field=1`) | `sid` | `op`, `article_ids`, `mode`, `field` |
| `updateArticle` (note, `field=3`) | `sid` | `op`, `article_ids`, `field`, `data` |
| `catchupFeed` | `sid` | `op`, `feed_id`, `is_cat`, `mode` |
| `unsubscribeFeed` | `sid` | `op`, `feed_id` |
| `getPref` | `sid` | `op`, `pref_name` |
| `setArticleLabel` | `sid` | `op`, `article_ids`, `label_id`, `assign` |
| `shareToPublished` | `sid`; credential/token components inside `url`, if present | `op`, `title`, `content`, `url` except its credential/token components |
| `subscribeToFeed` | `sid`; credential/token components inside `feed_url`, if present; optional `login` and `password` if supplied in future (not sent today) | `op`, `category_id`, `feed_url` except its credential/token components |

For every call, the endpoint URL is separately sanitized: embedded username/password and query values for every name in the known credential/token list above are replaced with `VALUE_REDACTED`. All other URL query parameters retain their values. The same rule applies to URL-valued body parameters; it does not hide ordinary article/feed URLs. HTTP Basic-auth headers are not part of the diagnostic.

The API reference defines authentication roles, not a privacy-redaction standard. Keeping search text, notes, and content is the user's chosen diagnostic policy, not an upstream recommendation.

## Todos

1. Implement request-scoped timeout context in `ttrssreader/src/main/java/org/ttrssreader/net/JSONConnector.java`.
   - Capture the built request URL and sanitized JSON body for execution failures, and the response request URL plus the original call's sanitized body for body-read failures. Keep original POST context even when the response followed a redirect.
   - Extract `feed_id` from the captured request parameters for feed-targeting operations and resolve its title and source URL from the existing local feed record. Respect operation semantics such as `is_cat`, avoid lookup for category IDs, and pass the optional feed context into the timeout formatter without adding a network request.
   - Keep feed metadata request-scoped alongside the endpoint URL and JSON so delayed body-read timeouts report the same feed that initiated the request.
   - Recognize typed network timeouts, including SocketTimeoutException and OkHttp timeout-style InterruptedIOException, without treating cancellation/interruption as a timeout.
   - Use one small shared formatter/redaction implementation for the user-visible message and log message; apply the per-call policy above.
   - Apply the existing URL credential/token redaction to the resolved feed URL before including it in either message or log.
   - Sanitize a diagnostic copy of the JSON, not the live payload. Replace the existing remove/log/restore behavior in `logRequest()` with copy-based credential/session redaction so ordinary request logs cannot expose the login username and cannot mutate the payload.
   - Add a narrowly scoped Reader wrapper for timeout reporting during lazy reads. Preserve the original exception type/cause, delegate close correctly, and avoid shared mutable "last request URL" state.
   - Record timeout errors in the existing connector error state and log the same redacted message with the exception.
   - Ensure the no-answer request path does not clear a recorded timeout as part of its empty-response workaround. Preserve that workaround for its intended empty-response case.
2. Add focused regression tests.
   - Inspect the existing build capabilities; no test sources or test dependencies were found.
   - Add minimal JVM test support only as needed for pure formatting/redaction and Reader behavior, without adding a new testing framework beyond what the focused tests require.
   - Verify exact VALUE_REDACTED output for embedded credentials and known token parameters, case variants, encoded values, and repeated parameters.
   - Cover every active operation/variant in the per-call table with representative JSON bodies; verify retained parameters exactly, optional-field absence, and username/password/sid replacement.
   - Cover nested credential/token components in `feed_url` and `url`, optional subscription credentials, and unchanged free-text search/note/title/content fields.
   - Verify `op: "login"` is retained and sanitization leaves the original body untouched, including credentials and optional fields.
   - Verify ordinary query values and API path are retained, original request URLs are not mutated, and messages retain exception details.
   - Verify feed-targeting timeout messages contain the matching local feed name and source URL, including delayed reads; category-targeting operations, missing feed records, and non-feed operations must not show incorrect feed metadata.
   - Verify credentials and known token query parameters in the resolved feed URL are redacted while its remaining URL and the feed name remain visible.
   - Verify execution and delayed-read timeouts carry their captured URL, including after settings changes; non-timeout failures and cancellation remain unchanged.
   - Verify timeout state survives the no-answer workaround and reaches the existing error consumers.
3. Update directly related documentation in README.md with the API endpoint and feed details shown for timeouts, the retained free-text fields, and credential/session redaction limits.
4. Validate the change.
   - Run focused JVM tests and the fdroid debug compilation/resource build through the existing GitHub Actions Android CI workflow; do not build Android APKs locally.
   - Check editor diagnostics and the final diff for unrelated changes.
   - If an Android device/emulator is available, reproduce an API response delay and confirm the dialog and log contain the same redacted endpoint and JSON body. Check long-body usability and whether Logcat splits/truncates long entries; do not silently truncate or omit required fields. Otherwise explicitly report that on-device verification was unavailable.

## Notes and considerations

- Existing dialogs in MenuActivity, ArticleFragment, SubscribeActivity, and ShareActivity consume connector errors; reuse them rather than introduce new UI.
- The static connector error state is pre-existing; do not redesign it as part of this task.
- Existing untracked Plans/ content is unrelated and must be left untouched.
- The known-parameter policy cannot identify arbitrary custom secret names or secrets embedded in paths/non-sensitive query values. Document this limitation rather than silently broadening redaction.
- Timeout diagnostics intentionally include potentially personal search text, article notes, titles, content, and ordinary URL values. Users should review these before sharing reports.
- Preserve original exception details, but ensure newly formatted diagnostic text does not reintroduce raw credential/session values via exception messages or causes. Keep exception type/stack context without emitting unsanitized request data.
- Do not perform unrelated parser, networking, or resource-lifecycle refactors.

## Follow-up implementation and validation

- [x] Capture the feed ID from the immutable request diagnostic body only for feed-specific operations; do not infer feed metadata for category requests.
- [x] Resolve a matching local feed record only when a timeout occurs, then carry its name and sanitized source URL through execution and delayed-read diagnostics.
- [x] Include feed metadata in the same formatted message used by the existing dialog and error log.
- [x] Add regression tests for feed operations, categories, missing records, URL credential/token redaction, and delayed response-body timeouts.
- [x] Document the feed name/source URL diagnostics and redaction in README.md.
- [x] Check editor diagnostics and `git diff --check` (using the repository's CRLF-aware whitespace setting).
- [x] Run the timeout-diagnostics/media-saving tests and F-Droid debug build through GitHub Actions; no local Android build was run.

Validation result: Android CI run [#38895094926](https://github.com/anno73/ttrss-reader/actions/runs/38895094926) passed, including the `Test timeout diagnostics and media saving`, `Build debug APK`, APK package/provider identity, and APK signing identity steps. The generated artifact was downloaded to `downloaded-apk/run-38895094926/ttrss-reader-debug.zip`.

Local validation limitation: the focused Gradle test task could not run because this machine has no Android SDK (`SDK location not found`). The initial attempt also exposed absent private Maven credentials; no local build was run. On-device timeout behavior was not manually verified in this session.
