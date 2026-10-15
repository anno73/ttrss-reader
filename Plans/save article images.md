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

- Source diagnostics and resource/manifest checks: pending.
- Android compile and CI unit tests: pending GitHub Actions run on `feature/save-article-images`.
- Device interaction test: pending.
