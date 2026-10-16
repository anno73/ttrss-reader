# Configuration and API reference

## Local setup

Edit [project configuration](../../../../ttrss.config.json), copy
[the dotenv template](../../../../.env.example) to project-root `.env`, and
enter credentials privately. `.env` is already Git-ignored. Do not overwrite an
existing `.env`; add only missing keys. The client does not read Android account
settings or use inherited environment variables.

```json
{
  "defaultProfile": "personal",
  "profiles": {
    "personal": {
      "apiUrl": "https://rss.example.net/tt-rss/api/",
      "timeoutSeconds": 30
    }
  }
}
```

Profiles must match `^[A-Za-z][A-Za-z0-9]*$`; names are case-insensitive.
`personal` derives `TTRSS_PERSONAL_USERNAME` and `TTRSS_PERSONAL_PASSWORD`.
Duplicate JSON keys (including case-only duplicates) and unknown properties
are errors. Every profile is validated before any request. An explicit unknown
profile never falls back to the default. Timeout must be an integer from 1
through 300 seconds, per request.

URLs must be absolute HTTPS URLs without user info, query strings or fragments.
Use the full endpoint, including any installation subdirectory and `/api/`.
The committed configuration has public example URLs, not a working instance.
If the real hostname is private, do not commit your configuration change.

### Dotenv grammar

- UTF-8, with or without BOM; CRLF and LF are supported.
- Blank lines and full-line comments beginning with `#` are ignored.
- Keys match `[A-Za-z_][A-Za-z0-9_]*`; split each entry on its first `=`.
- Trim whitespace surrounding keys and unquoted values. Optional matching single
  or double outer quotes are removed; their interior is preserved literally.
- No escapes, interpolation, `export` prefix, multiline values, or inline comments.
  `#`, `=`, `$`, and backslashes in values are literal. Choose outer quotes that
  differ from the value's boundary quotes when necessary.
- Duplicate keys, including case-only duplicates, are errors. Missing/empty
  selected credentials are errors; whitespace-only credentials are invalid.
- Other valid keys are permitted so this file can coexist with existing settings.
  It is never executed or exported into the process environment.

`.env` is plaintext, not encrypted. Restrict filesystem access to your account,
exclude it from shared backups/sync, and do not paste it into chat, logs or issues.
Git-ignore does not protect it against filesystem access by an agent or user.
Passwords and session IDs necessarily exist in managed memory during execution;
clearing references is not guaranteed memory erasure.

## Commands

| Command | Parameters | API operation |
|---|---|---|
| Check | none | `getVersion`, `getApiLevel` |
| GetFeeds | CategoryId (default -3: all feeds), Limit (50), Offset (0) | `getFeeds` |
| GetCategories | none | `getCategories` with nested and empty categories enabled |
| GetUnread | none | `getUnread` |
| GetHeadlines | FeedId (-4: all articles), IsCategory, ViewMode, Search, Limit, Offset | `getHeadlines` |
| GetArticle | ArticleIds (comma-separated positive integers) | `getArticle` |
| SetRead / SetUnread | ArticleIds, AllowMutation | `updateArticle`, field 2, mode 0 / 1 |
| SetStarred / SetUnstarred | ArticleIds, AllowMutation | `updateArticle`, field 0, mode 1 / 0 |

`Limit` is 1..200, `Offset` is 0..1000000. Supported view modes are
`all_articles`, `unread`, `marked`, `updated`, `adaptive`. Article IDs are
positive 32-bit integers, unique, at most 100 per invocation. Parameters that
do not apply to a command are rejected, rather than silently ignored.
Categories are returned in one response because the upstream method has no
pagination. Response bodies are limited to 4 MiB; ask for fewer records on error.

## Protocol, results and failures

The client sends JSON POSTs with `Content-Type: application/json; charset=utf-8`,
checks HTTP and API status, uses `sid` after login, and attempts logout in a
`finally` block. Credentials and session IDs are not returned or persisted.
Redirects, cookies, insecure TLS bypasses and automatic retries are disabled.
Normal system proxy and certificate trust settings apply.

Success: exit 0, one JSON object:

```json
{"ok":true,"profile":"personal","command":"GetFeeds","data":[],"error":null}
```

Failure: exit 1, one JSON object with `ok:false` and
`error:{"code":"...","message":"..."}`. Known API codes are reported safely:
`API_DISABLED` (enable API access), `LOGIN_ERROR` (check local credentials),
`NOT_LOGGED_IN` (session expired), `UNKNOWN_METHOD` (backend capability absent),
`INCORRECT_USAGE`, `E_OPERATION_FAILED`, `E_NOT_FOUND`. Unknown error text is
never echoed. Transport failures do not include raw exception text, URLs,
request/response bodies, credentials or session IDs. No re-login/replay occurs.
TLS, connection and timeout failures share `TRANSPORT_ERROR`; check connectivity,
certificate trust, and timeout locally without disabling verification.

Logout failure makes the invocation fail, even when the operation succeeded.
In that case `operationSucceeded:true` and `data` preserve the completed result.
When both fail, the original error is preserved and a separate `cleanupError`
reports logout failure. A failed mutation may have partially or fully applied:
inspect state before any retry. Login responses and session IDs are never data.

Backend result data and diagnostic messages also redact exact occurrences of
the selected credentials and session ID as defense in depth. Fixed output field
names and allowlisted error codes remain intact, even if a credential happens
to match them. This is not a general data-loss-prevention system.

## Compatibility and sources

Protocol checked against upstream tt-rss commit
`270fcc71f867b4669e330332aaf60e329107fc06` (API level 23):

- [API entry point](https://github.com/tt-rss/tt-rss/blob/270fcc71f867b4669e330332aaf60e329107fc06/api/index.php)
- [API handlers and inline parameter comments](https://github.com/tt-rss/tt-rss/blob/270fcc71f867b4669e330332aaf60e329107fc06/classes/API.php)

No minimum version is guessed: `Check` reports the server's API level and
unsupported methods fail explicitly. Retrieval requests ask for sanitized
content, suppress `force_update`, and send `mark_read:false` for older servers.
The current upstream handler does not consume `mark_read`.

## Validation

The Windows PowerShell 5.1 contract tests run in the `ttrss-skill` job of
[Android CI](../../../workflows/android-build.yml), without a real backend or
secrets. They use only .NET/PowerShell built-ins. No package installation is needed.
Live testing requires explicit user authorization and a designated test account:
check connectivity, compare unread state before/after reading, and verify that
only explicitly selected IDs change. No live test is run automatically in CI.
