# Configurable API page size

## Approved scope
- Add **Maximum items per API request** in Connection settings, default **200**.
- Accept positive 32-bit integers; reject invalid input visibly without saving.
- Honor per-Wi-Fi overrides and normal default/reset handling.
- Headlines clamp to 200 (60 below API level 6) and remaining download budget.
- Real feeds use the configured limit/offset with no additional endpoint cap.
- Retain non-pageable labels/virtual feeds once; leave categories and mutations unchanged.
- Preserve total-download budgets and surface failures without replaying pages.

## Execution
1. Created `feature/configurable-api-page-size` from master `1b2f8a9f`.
   The pre-existing untracked TODO.md remains untouched.
2. Added constants, numeric preference UI, visible validation, Controller access
   with per-Wi-Fi/global lookup and diagnostic default fallback, English/German strings.
3. Applied page sizing to headlines and implemented real-feed pagination, counting
   raw pageable records separately from metadata and filtered/deduplicated results.
   Headline totals use long arithmetic; remaining budgets shrink the final request.
   Repeated pages and offset overflow fail explicitly. Feed failures discard partial
   lists to prevent replacing the cached feed list with an incomplete result.
   Paginated requests do not automatically replay expired sessions; a later logical
   download can log in again. Readers close even when preparation fails.
4. Added net-package request-level regression tests and preference/default/reset
   tests; documented behavior in README. No workflow selector changes are needed.
   Tests cover omitted records whose ID occurs after the filter-triggering field.
5. Committed implementation/tests/documentation as
   `92cb5b03c60886b4e64b63b11776e5fff1a2c940` and pushed the task branch.
   Verified the upstream branch has that exact source commit.
6. Verified `.github/workflows/android-build.yml` run **39156906860** succeeded
   for that commit: unit tests, both debug APK builds, APK version/package/provider
   checks, signing verification, and artifact upload all passed. The backend skill
   job also passed.
7. Downloaded artifact **12147138833** (`ttrss-reader-debug`) and verified its ZIP
   SHA-256 against GitHub's published digest:
   `780a1b0cd375483778f521a118fafc08940a480ddd51e8fdc707668db413267f`.
   Retained the ZIP and extracted directly into `downloaded-apk/run-39156906860/`.
   Verified all three required files exist:
   - `ttrss-reader-debug.zip`
   - `ttrssreader/build/outputs/apk/fdroid/debug/ttrssreader-fdroid-debug.apk`
   - `ttrssreader/build/outputs/apk/play/debug/ttrssreader-play-debug.apk`
8. This final plan update is documentation-only; its commit is separate from the
   tested source commit and does not change the verified APK code. Commit/push it
   with CI skipped to avoid rebuilding identical source solely to record results.
9. Request manual-test results. Merge only after passed manual tests and explicit
   approval; no merge has been performed.

## Validation and limitations
- CI: https://github.com/anno73/ttrss-reader/actions/runs/39156906860
  completed successfully; APK builds were not skipped.
- Editor diagnostics reported no errors in the changed Java implementation/test
  files. `git diff --check` with CRLF-aware whitespace configuration passed.
  These are static checks only, not compilation/test validation.
- No local builds, Gradle tasks, project-compiling tests, or Android SDK setup.
- Backend sources: https://github.com/tt-rss/tt-rss/blob/HEAD/classes/API.php
  (`getHeadlines`, `getFeeds`, `_api_get_feeds`, `getCategories`).
- Labels/virtual feeds are not covered by backend feed pagination; response count
  can exceed the real-feed limit. Large allowed feed limits can create large responses.
- Offset pagination cannot guarantee a snapshot while backend data changes.
- Manual device testing is pending; no merge is authorized.

## Manual checks for the downloaded APK
1. Confirm Connection settings show default 200 and a numeric keypad. Try 1,
   a custom value such as 25, and a value above 200.
2. Confirm empty, zero, negative, nonnumeric, and overflowing input cannot replace
   the saved setting and show an error where entry/paste is possible.
3. Fetch headlines and feeds spanning multiple pages. Confirm complete results,
   preserved labels/virtual feeds, and unchanged total-download behavior. Request
   diagnostics/logs should show bounded limit values and correct skip/offsets.
4. Verify per-Wi-Fi overrides, disabled-network/global fallback, and defaults/reset.
   Change the setting and fetch again without restarting.
5. Check a connection failure remains visible and does not replace the cached feed
   list with a partial list. Legacy API headline compatibility is covered by CI mocks;
   use a legacy backend for additional device confirmation if available.
