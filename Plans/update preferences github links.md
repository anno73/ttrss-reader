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
- No local Android build or tests were run, per repository instructions.
- GitHub Actions workflow run and APK artifact status: pending.
- Branch commit and push status: pending.

## Manual acceptance and merge gate

Please verify the project and feedback entries in the app's Preferences menu
open the repository home and Issues page, respectively. Do not merge this
branch unless manual acceptance has passed and the user explicitly approves
the merge.
