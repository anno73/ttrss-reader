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
- GitHub Actions compilation, tests, APK verification and artifact download:
  pending the implementation commit/push.

## Delivery and acceptance

- Commit and push all task files, excluding unrelated changes and build outputs.
- Verify `.github/workflows/android-build.yml` succeeds for the implementation.
- Retain `downloaded-apk/run-{RUN-ID}/ttrss-reader-debug.zip`; extract directly
  into that directory and verify both original flavor APK paths.
- Update this plan with the actual CI run, tests and artifact results, commit
  the validation record, and verify the final upstream push.
- Manual tests are pending: upgrade and reopen without release notes; fresh
  install welcome once; invalid-server handling; feed sync; version display;
  Preferences without crash-report settings; external project links.
- Do not merge to `master` until the user reports successful manual tests and
  explicitly approves merging.
