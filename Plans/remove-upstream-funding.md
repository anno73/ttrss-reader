# Remove former-maintainer funding references

## Objective

Remove donation requests and sponsor links belonging to the previous maintainer from this fork, while retaining project lineage credits and licensing information.

## Changes

- Removed the README Donations section and its donation-only PayPal/image/wiki references.
- Removed `.github/FUNDING.yml`, which directed GitHub Sponsors and custom PayPal funding to the previous maintainer.
- Kept the README's credit to the original Google Code `ttrss-reader` and intermediate `nilsbraden/ttrss-reader-fork`.
- Kept `ttrssreader/LICENSE` unchanged.

## Validation

- `git diff --check` passed; the focused diff removes only the former maintainer's donation material and funding configuration.
- GitHub Actions run [38951556624](https://github.com/anno73/ttrss-reader/actions/runs/38951556624) completed successfully. The tt-rss skill test, Android unit test task, debug APK build, package/provider checks, signing verification, and artifact upload all passed.
- Downloaded and verified the ZIP and both APKs under `downloaded-apk/run-38951556624/`, preserving the archive paths:
  - `ttrss-reader-debug.zip`
  - `ttrssreader/build/outputs/apk/fdroid/debug/ttrssreader-fdroid-debug.apk`
  - `ttrssreader/build/outputs/apk/play/debug/ttrssreader-play-debug.apk`
- No local Android compilation or tests will be run, in accordance with repository validation requirements.
- No merge will be performed without successful manual tests and explicit user approval.
