# Save article images

## Scope

- Save image and video media opened in an article to the public `Pictures/tt-rss` directory while retaining the source filename and extension.
- Add a Save action to the existing long-press context menu.
- Add an opt-in, disabled-by-default triple-tap setting without changing the existing double-tap star behavior.

## Implementation

- Use MediaStore with a `Pictures/tt-rss` relative path on Android 10 and newer; use the public Pictures directory and media scanning on older Android versions.
- Keep context-menu saving available independently of the triple-tap setting.
- Add the setting to Usage preferences and provide English and German labels.

## Validation

- Source diagnostics report no errors; edited manifest and resource XML files parse successfully.
- The first GitHub Actions build identified an unavailable WebView video hit-test constant. Video links now use their file extensions for context-menu detection.
- GitHub Actions run `38658379476` passed the timeout-diagnostics unit tests, built both debug APK flavors, and verified their signing identity.
- Downloaded the CI artifact to `downloaded-apk/run-38658379476/ttrss-reader-debug.zip` and extracted both APKs there.
- Device interaction test: pending.
