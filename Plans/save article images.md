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
- Folder-picker CI and APK artifacts: pending.
- Device testing of the folder-picker implementation: pending. Verify first-save selection and resumption, cancelling, persistence after restart, Settings changes/cancellation, revoked grants, duplicate names, JPG/PNG/GIF/WebM, and unchanged long-press/triple-tap/double-tap behavior.
- No merge until manual tests pass and the user explicitly approves.
