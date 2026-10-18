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
5. Commit/push and validate exclusively with `.github/workflows/android-build.yml`.
6. Download the run ZIP and extract both APK paths directly in
   `downloaded-apk/run-{RUN-ID}/`.
7. Record validation results, commit/push the final plan, request manual-test results.
   Merge only after passed manual tests and explicit approval.

## Validation and limitations
- Implementation and tests written; CI and artifacts not yet verified.
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
