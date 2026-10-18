# Remove release notes and legacy crash reporting

## Agreed scope

Remove the in-app release-notes page and its startup/version-detection mechanism.
Following clarification, also remove the legacy email crash reporter, its
background minimum-supported-version request, eligibility checks and settings.
Preserve repository changelogs, external project/download links, package version
display, first-install welcome, server validation, feed syncing and APK signing.

## Executed work

1. Created `remove-release-notes-and-crash-reporting` from `master` at `5331720e`.
   Left the unrelated untracked `TODO.md` untouched.
2. Removed the release-notes startup branch, dialog, layout, HTML template,
   bundled update array and 15 release-note strings across six locales.
3. Removed the email crash reporter and its hooks in eight activities, both
   crash-report preferences and their 16 localized strings, the background HTTP
   version request, cached version/reporting state and reporter-only installation
   eligibility checks.
4. Retained only a read-only compatibility check of the historical `LastVersionRun`
   marker for legacy first-run detection. No installed version is compared,
   recorded or used to trigger a dialog or background task.
5. Corrected the tightly coupled `getSinceId()` recovery path: it called the
   removed version-state setter instead of resetting the article ID preference.
6. Added seven JUnit/Mockito startup regression tests covering fresh installs,
   repeat launches, existing-install upgrades, legacy markers, connection
   validation and article-ID recovery. Included the new test class in Android CI.
7. Updated the README's version/startup documentation. Historical changelogs and
   attribution remain unchanged. Historical inactive preference entries are left
   inert on disk; no user data is cleared.

## Validation

- Changed XML resources parse successfully.
- Static reference checks found no remaining active release-note UI, crash-report
  hooks/settings or minimum-supported-version request. The only historical
  reporter reference is its attribution in `thanks.xml`.
- Editor diagnostics reported no errors in the modified Controller, Utils or
  new startup test class.
- Whitespace review uses `git -c core.whitespace=cr-at-eol diff --check` because
  existing Java/XML/README files are stored with CRLF line endings.
- No local compilation, Gradle tasks or unit tests were run.
- Implementation commit: `8398c7fcd99e09383976bd0e1eefc2c4fefda008`,
  pushed and verified against the task branch's GitHub upstream.
- [Android CI run 39164673421](https://github.com/anno73/ttrss-reader/actions/runs/39164673421)
  completed successfully for that commit. Both `build` and `ttrss-skill` passed.
- CI executed build-version verification, existing network/media unit tests and
  `StartupBehaviorTest`; the unit-test Gradle invocation completed successfully.
- Both debug flavors built successfully. CI verified their package/provider
  identity, generated versions and persistent signing identity.
- Artifact `12150336106` (`ttrss-reader-debug`) downloaded successfully. Its ZIP
  SHA-256 matches GitHub's artifact digest:
  `01965935699cd27457e5708f28789dbb503164530fb228ce90b35fdd1278beb7`.
- Verified files, with original archive paths preserved:
  - `downloaded-apk/run-39164673421/ttrss-reader-debug.zip` (21,268,156 bytes).
  - `downloaded-apk/run-39164673421/ttrssreader/build/outputs/apk/fdroid/debug/ttrssreader-fdroid-debug.apk`
    (11,994,783 bytes).
  - `downloaded-apk/run-39164673421/ttrssreader/build/outputs/apk/play/debug/ttrssreader-play-debug.apk`
    (12,520,220 bytes).
- The only CI annotation was an existing checkout-action Node.js deprecation
  notice; it did not prevent validation or artifact publication.

## Delivery and acceptance

- All implementation files and this executed plan are committed on the task
  branch. APKs and the ZIP are Git-ignored; `TODO.md` remains untouched.
- This validation record is delivered in a separate documentation-only
  `[skip ci]` commit; the tested application/workflow code remains unchanged.
- Installed the Play APK with `adb install -r` on the connected Samsung SM-S928B,
  preserving app data. Verified installed version
  `remove-release-notes-and-crash-reporting_2026-10-18_20-44-28`
  (`versionCode` 214519468).
- The user reported manual tests passed after being asked to check upgrade and
  reopening without release notes, feed syncing and absent crash-report settings.
  Fresh-install and other scenarios were covered by CI where applicable; no
  separate device result was recorded for those scenarios.
- The user explicitly approved merging this branch into `master`.
- Merged and pushed to `master` as `6585ac7433aa78e9f0e5e32d017fe47775516ea1`;
  verified the upstream commit.
- [Merged-master Android CI run 39165263256](https://github.com/anno73/ttrss-reader/actions/runs/39165263256)
  passed, including unit tests, both APK builds and identity/signature checks.
- Downloaded artifact `12150560763` and verified its ZIP SHA-256:
  `99ea673325ba72fc22a0ad375e387d8ab4d7d5ae2bcdc167bfd530867b68ea8e`.
  Retained `downloaded-apk/run-39165263256/ttrss-reader-debug.zip` (21,268,124
  bytes) and verified extracted F-Droid (11,994,734 bytes) and Play (12,520,184
  bytes) APKs under their original `ttrssreader/build/outputs/apk/` flavor paths.
- The manually accepted task-branch Play build remains installed on the device;
  the newer master artifacts were downloaded, not installed.
