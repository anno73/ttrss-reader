# Save article images

## Scope

- Save image and video media opened in an article to a user-selected folder while retaining the source filename and extension.
- Add a Save action to the existing long-press context menu.
- Add an opt-in, disabled-by-default triple-tap setting without changing the existing double-tap star behavior.

## Implementation

- Use the system folder picker (`ACTION_OPEN_DOCUMENT_TREE`) on first save and remember the selected tree URI with persisted read/write access.
- Save directly into the chosen folder using `DocumentsContract` and `ContentResolver`, on all supported Android versions. Users can create/select `Pictures/tt-rss` themselves.
- Allow changing the folder under Settings > Usage > Media save folder, using the same picker and grant validation.
- Resume the pending save once after selection, preserve its URL through fragment recreation, and cancel it if the picker is cancelled.
- When a stored grant is revoked or the folder cannot create files, explain the problem and reopen the picker on the next save. Never silently change the destination.
- Preserve downloaded bytes without conversion; consider Content-Disposition for the filename, preserve source extensions, avoid overwriting duplicate names, and report the provider-assigned saved name.
- Remove newly created incomplete documents if a write fails; log cleanup failures without hiding the original error.
- Do not request storage permissions for this media-saving path. Preserve the separate legacy attachment downloader and its permission flow.
- Do not gate SAF saves on the primary external-storage mount state: the selected provider may be on another volume or in the cloud.
- Keep context-menu saving available independently of the triple-tap setting.
- Add the setting to Usage preferences and provide English and German labels.

## Validation

- Source diagnostics report no errors; edited manifest and resource XML files parse successfully.
- The first GitHub Actions build identified an unavailable WebView video hit-test constant. Video links now use their file extensions for context-menu detection.
- GitHub Actions run `38658379476` passed the timeout-diagnostics unit tests, built both debug APK flavors, and verified their signing identity.
- Downloaded the CI artifact to `downloaded-apk/run-38658379476/ttrss-reader-debug.zip` and extracted both APKs there.
- User testing found saving did not create a visible file. Investigation found the legacy-storage permission callback was routed to `ArticleFragment`, while the `@AfterPermissionGranted` method was on `ArticleWebViewClient`; that annotation could not resume the pending save. The callback is now handled explicitly by the fragment and permission denial is reported.
- Added visible saving/success/failure feedback and handling for known MediaStore runtime failures, so a failure will show a message instead of appearing to do nothing.
- Permission-flow and diagnostics CI run `38660009093` passed and its APKs were downloaded.
- Device testing of that version reported "Primary directory Pictures not allowed" for the MediaStore.Files insertion. This is a collection/path restriction, not a missing runtime permission; the SAF implementation replaces that path.
- Added focused JUnit/Mockito tests for persisted folder permissions, invalid/cancelled selection, rejected grants, MIME/filename decisions, duplicate naming, exact output bytes, provider-assigned names, and write/cleanup failure handling.
- Extended the existing GitHub Actions unit-test command to include the media-save tests.
- Initial folder-picker CI run `38816533928` passed the expanded unit-test command, built both debug APK flavors, and verified their signing identities.
- Code validation run `38816880959` passed the expanded unit-test command, built both APK flavors, and verified their signing identities, including the picker-hint guard and existing-grant regression test.
- Downloaded and extracted both APKs under `downloaded-apk/run-38816880959/`. Verified the downloaded ZIP against GitHub's artifact SHA-256 digest (`5058dc224eb60ac71141556ed9fcdc05ec86342d34ff4c0e5c70e589b5658668`).
- Source diagnostics report no errors and the English/German strings and Usage preference XML parse successfully. Local test discovery did not find the Android JVM tests; the required GitHub Actions Gradle command ran them instead. No local APK build was attempted.
- Final review removed the old primary-storage mount-state gate from the SAF worker and added a worker-level regression test proving that saving invokes the selected provider even when primary external storage is unmounted.
- Final code CI run `38817408359` passed unit tests, both APK builds, and signing checks. Downloaded both final APKs under `downloaded-apk/run-38817408359/` and verified the artifact SHA-256 (`dda312d9ba7c3c77ebb43e8c5244c514467572f9684b5d82bdbceaee38906857`).
- User reported that manual tests of the final folder-picker implementation passed. Requested checks included first-save selection and resumption, cancelling, persistence after restart, Settings changes/cancellation, revoked grants, duplicate names, JPG/PNG/GIF/WebM, and unchanged long-press/triple-tap/double-tap behavior.
- Manual-test result is user-reported; no device testing was performed in this development environment.
- Merged into master after successful feature CI, user-reported manual tests, and explicit user approval.
- Filename regression follow-up: prefer the selected image's source URL, or a unique image URL from the same article's stored remote files, over TT-RSS's `public.php?op=cached` proxy URL. If the original name is unavailable or ambiguous, use a `yyyyMMdd-HHmmss` basename and the response-inferred media extension; existing duplicate-name handling remains in force.
- Added regression coverage for original-source filenames, `Content-Disposition` filenames, and timestamp/extension fallback. Source diagnostics passed; GitHub Actions results will be recorded after the task branch is pushed.
