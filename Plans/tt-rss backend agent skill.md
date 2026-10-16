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
- Test implementation and CI verification in progress.

## Validation and limitations

- Upstream protocol checked against tt-rss commit
  `270fcc71f867b4669e330332aaf60e329107fc06`, API entry point and API handlers.
- No real backend has been contacted. Live/manual tests require a configured
  endpoint, privately populated `.env`, and explicit authorization.
- `.env` is plaintext. Git-ignore and skill instructions are not an isolation
  boundary against an agent with filesystem access.
- Workflow result and APK download: pending.
