# Update Preferences GitHub links

## Problem and approach

The Preferences menu linked to the former `nilsbraden/ttrss-reader-fork`
repository. Update only the menu's project and feedback destinations to the
current repository, `anno73/ttrss-reader`. The project link goes to the
repository home; feedback goes to its Issues page.

## Implementation

1. Created branch `fix/preferences-github-links` from `master`.
2. Updated the two VIEW intent URLs in
   [prefs_main.xml](../ttrssreader/src/main/res/xml/prefs_main.xml):
   - Project: `https://github.com/anno73/ttrss-reader`
   - Feedback: `https://github.com/anno73/ttrss-reader/issues`
3. Kept the change limited to the Preferences menu; no localized strings or
   unrelated repository links were changed.

## Validation and delivery

- `git -c core.whitespace=cr-at-eol diff --check` passed for the XML diff.
- Preferences XML was parsed successfully; both expected destinations are
  present and the former fork URL is absent.
- No local Android build or tests were run, per repository instructions.
- [GitHub Actions run 39161057147](https://github.com/anno73/ttrss-reader/actions/runs/39161057147)
  succeeded for commit `9a980dcae71e4b143e436c2c3c4a4bca86dcab85`: targeted
  unit tests, both debug APK builds, APK version/package/provider checks,
  signing verification, and the tt-rss skill test all passed.
- Downloaded the `ttrss-reader-debug` artifact to
  `downloaded-apk/run-39161057147/` and extracted it without flattening paths.
  Verified the ZIP and both APKs:
  - `ttrss-reader-debug.zip` (21,298,747 bytes)
  - `ttrssreader/build/outputs/apk/fdroid/debug/ttrssreader-fdroid-debug.apk`
    (12,025,922 bytes)
  - `ttrssreader/build/outputs/apk/play/debug/ttrssreader-play-debug.apk`
    (12,551,327 bytes)
- Branch commit `9a980dca` was pushed successfully. The downloaded APKs are
  generated artifacts and are not staged for commit.

## Manual acceptance and merge gate

Please verify the project and feedback entries in the app's Preferences menu
open the repository home and Issues page, respectively. Do not merge this
branch unless manual acceptance has passed and the user explicitly approves
the merge.
