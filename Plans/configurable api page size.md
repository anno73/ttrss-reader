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
- In-place device installation passed as recorded below. The user confirmed that
  manual page-size/download tests passed and existing settings/data are intact.
  The user explicitly approved merging `feature/configurable-api-page-size` into
  `master`; the approved merge and merged-build validation are complete below.

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

## Follow-up: reported Play APK update failure

The user reported "App not installed" on Samsung Galaxy S24 Ultra / Android 16
when updating `master_2026-10-18_18-16-18` through Package Installer. Investigated
the local Play APKs from `downloaded-apk/run-39153383913/` and
`downloaded-apk/run-39156906860/`; no rebuild or speculative signing change was made.

### Detailed artifact comparison

| Property | Installed master artifact | New feature artifact |
| --- | --- | --- |
| Package ID | org.ttrssreader.anno73 | org.ttrssreader.anno73 |
| Version code | 214510578 | 214513350 |
| Version name | master_2026-10-18_18-16-18 | feature_configurable-api-page-size_2026-10-18_19-02-30 |
| Minimum / target SDK | 23 / 36 | 23 / 36 |
| APK ZIP entries | 1306 | 1306 |
| Signer SHA-256 | 28A981D4BF6D96A6D775BE02E3D69DECE5228C52DDC4FC17F39FB3E788AD9A79 | 28A981D4BF6D96A6D775BE02E3D69DECE5228C52DDC4FC17F39FB3E788AD9A79 |

- Binary manifests have the same 81 start elements. Differences are the intended
  version name/code and three activity-label resource IDs shifted by added strings;
  package/provider identities, SDK requirements, permissions and component flags
  are unchanged. No APK incompatibility was found in these checks.
- Old Play APK SHA-256:
  `2e1550ef72946dcbe28777831522465b1e812c0538aaa84e44216964f075b8b8`.
- New Play APK SHA-256:
  `30cf223f1478e8a7f0e6e6af95ebc77cb6f168a38a394d6560b24dfb7598491f`.

### Device evidence and recovery

- Used the existing adb installation and the user's explicitly identified USB
  device. No local Android SDK installation, compilation, or Gradle execution.
- Device Package Manager confirmed the installed old version/code above.
- Retained failed Package Installer sessions **65419066** and **466512570** are
  for this package and failed with **INSTALL_FAILED_VERIFICATION_FAILURE (-22)**,
  "Install not allowed for file:///data/app/vmdl...tmp".
- Logs show both Google Play and Samsung device-security verifiers were consulted.
  The available evidence does not identify which verifier rejected the sessions
  or its policy rationale. No transfer corruption or Telegram-specific defect
  is established. Stopped looking for device-downloaded APKs at the user's request;
  all artifact comparisons and the successful update use the local downloaded-apk file.
- `adb -s <authorized-device> install -r <new-local-play-apk>` returned **Success**.
  Session **336362204** completed with result **1**, and Package Manager explicitly
  logged "Retain data and using new".
- Post-install package inspection confirmed new version **214513350** /
  **feature_configurable-api-page-size_2026-10-18_19-02-30**. The app's credential-
  encrypted and device-encrypted data-directory inode IDs stayed unchanged.
  No uninstall, data clear, verifier-setting change, or signing-key rotation occurred.
- This resolves the current update by an observed in-place installation. Normal
  Package Installer/source-specific verification behavior remains unverified; do
  not claim a code fix for the verifier rejection or complete manual feature tests.
- Added directly related README troubleshooting distinguishing device-verifier
  rejection from signing/version/provider errors and documenting targeted,
  data-preserving ADB diagnosis. This follow-up is documentation-only, requiring
  no new compilation/test run; the APK source and successful CI remain unchanged.
- The user subsequently confirmed **manual tests passed and existing settings/data
  are intact**. This validates the feature and data-preserving in-place update on
  the connected Samsung device. It does not establish that the original
  non-ADB installer-verifier rejection is resolved.
- After that confirmation, the user explicitly selected **Merge into master**.
  Completed the approved merge and verified its GitHub Actions build/artifacts.

## Approved master merge and final validation

- Merged the tested feature branch into up-to-date master using merge commit
  `137e99b8206d058ab4e03415c7e77b57ffb0bf2b`. Pushed master and verified that
  origin/master resolves to that commit.
- [Android CI run 39159825420](https://github.com/anno73/ttrss-reader/actions/runs/39159825420)
  **passed** for that exact merged commit. Unit tests, version-generation checks,
  both debug APK builds, package/provider/version/signing checks, backend-skill
  tests, and artifact upload all completed successfully.
- Downloaded artifact **12148278448** and verified its SHA-256:
  `39371bc70975ec894559a6b09ff681cb0f6ebc607949c32173f13537f1155292`.
- Verified the required retained ZIP and directly extracted APK paths:
  - `downloaded-apk/run-39159825420/ttrss-reader-debug.zip` (21,298,714 bytes).
  - `downloaded-apk/run-39159825420/ttrssreader/build/outputs/apk/fdroid/debug/ttrssreader-fdroid-debug.apk`
    (12,025,891 bytes).
  - `downloaded-apk/run-39159825420/ttrssreader/build/outputs/apk/play/debug/ttrssreader-play-debug.apk`
    (12,551,311 bytes).
- The final execution-record commit changes documentation only and is pushed
  separately with CI skipped. The tested merged source and downloaded APKs are
  unchanged. The unrelated untracked TODO.md remains untouched/uncommitted.
- Device/manual tests passed on the feature APK before the approved merge.
  The final master APKs are built and CI-verified, not additionally installed
  on the device. Normal non-ADB installer-verifier behavior remains a documented
  limitation, not an APK signing/version regression.
