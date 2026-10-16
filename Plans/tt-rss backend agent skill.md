# tt-rss backend agent skill

## Scope and plan

- Branch: `feat/ttrss-backend-skill`, based on upstream `master`.
- Create a workspace skill with a dependency-free Windows PowerShell 5.1 client.
- Store HTTPS endpoints and timeouts in project-root `ttrss.config.json`.
- Read credentials only from project-root `.env`, using
  `TTRSS_<UPPERCASE_PROFILE>_USERNAME` and `_PASSWORD`; commit placeholders only.
- Support connectivity/version checks, feeds, categories, unread counts,
  headline search, article retrieval, and explicit read/star state changes.
- Reject ambiguous configuration, unsupported parameters, redirects, and unsafe
  mutation retries; retain sessions only in memory and attempt logout.
- Add offline contract tests to a Windows job in Android CI. Run no local
  compilation or project tests. Push, verify CI and download both APK artifacts
  using the repository's required layout.
- Commit completed work and validation results; do not merge without successful
  manual tests and explicit user approval.

## Completed implementation

- Added skill instructions, configuration/API reference, client/module, public
  example profile configuration and dotenv template.
- Reused existing `.env` ignore rule; no real credentials were read or created.
- Added README setup guidance and Windows PowerShell contract-test job.
- Added offline contract tests with an in-memory HTTP handler, exercising
  configuration/dotenv parsing, argument guards, request mapping, pagination,
  API/HTTP errors, size limits, cleanup, and secret-safe JSON output.

## Validation and limitations

- Upstream protocol checked against tt-rss commit
  `270fcc71f867b4669e330332aaf60e329107fc06`, API entry point and API handlers.
- After the user configured credentials and explicitly requested a live check,
  ran `Check` with profile `test`. The backend was reachable but returned
  `API_DISABLED` during login (exit 1). Credentials could not be verified because
  the upstream handler checks API enablement before authentication. Enable API
  access for that tt-rss account before retrying; no article state was changed.
- `.env` is plaintext. Git-ignore and skill instructions are not an isolation
  boundary against an agent with filesystem access.
- Initial CI run `38927238024` exposed a missing .NET assembly reference in the
  test fixture compiler. Added explicit framework references; no external
  dependency or package installation is required.
- Run `38927423283` succeeded: 91 PowerShell tests passed and both Android
  flavors built, with package/provider identity and signing checks passing.
- Downloaded `ttrss-reader-debug.zip` to `downloaded-apk/run-38927423283/`,
  verified SHA-256 against the GitHub artifact digest, and extracted directly
  into that directory. Verified both
  `ttrssreader/build/outputs/apk/fdroid/debug/ttrssreader-fdroid-debug.apk` and
  `ttrssreader/build/outputs/apk/play/debug/ttrssreader-play-debug.apk` exist.
- Final hardening preserves fixed error-schema keys and allowlisted error codes
  even when credentials coincide with protocol words; a regression test covers
  this.
- Final executable code at `b98b4e32` passed workflow run `38927846138`:
  92 PowerShell tests passed, zero failed; Android tests, both APK builds,
  package/provider checks and signing checks succeeded.
- Downloaded the final code-run archive to
  `downloaded-apk/run-38927846138/ttrss-reader-debug.zip`, checked its SHA-256
  (`5d022cb53c339102a6eb431b4ea32fe212ce736fbacf577cde0f7d5777a42d5e`)
  against the artifact digest, and verified both APKs in the same required
  extraction layout. ZIP: 21,295,840 bytes; F-Droid APK: 12,022,215 bytes;
  Play APK: 12,547,616 bytes.
- No local compilation, builds or tests were run. Editor diagnostics reported
  no errors in the PowerShell files, workflow or configuration schema.
- The final plan-only documentation commit uses `[skip ci]`; executable content
  is unchanged from the verified run above.
- Local user edits to configuration and an untracked configuration copy appeared
  during implementation. They are preserved and excluded from task commits.
- Successful live/manual verification remains blocked on backend API enablement;
  the branch has not been merged.
