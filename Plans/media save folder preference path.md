# Show media save folder preference path

## Scope

- Show the selected media folder as its full user-visible path in Usage preferences.
- Include the storage root label shown by the system folder picker, such as `Internal storage/Pictures/ttrss`.
- Omit opaque volume identifiers and allow the complete summary to wrap instead of truncating.
- Leave folder selection, persisted access grants, and media saving behavior unchanged.

## Implementation

- Use `DocumentsContract.findDocumentPath` and provider root metadata to render the visible root title and ancestor display names on supported Android versions.
- For older Android versions, reconstruct External Storage Provider paths from the provider's volume-relative document ID and root title.
- Preserve the selected folder's display name as a fallback when a provider does not expose ancestry.
- Use the same path formatter in the media-saved confirmation.
- Give only the media-folder preference an unlimited, non-ellipsized summary view.
- Add focused tests for provider ancestry and External Storage Provider paths.

## Validation

- Editor diagnostics: no errors in the modified Java files.
- Usage preference XML parsed successfully.
- GitHub Actions run `39129627378` passed the media-saving and network unit tests, both debug APK builds, package/provider identity checks, and signing verification.
- Downloaded the run's artifact to `downloaded-apk/run-39129627378/ttrss-reader-debug.zip` and extracted it into the same directory. Verified the archive SHA-256 `d20df139f6df84ffe0b041a5c151d9552578243d63e6b03c28deaec95f4ab717` and confirmed both APKs exist at:
  - `downloaded-apk/run-39129627378/ttrssreader/build/outputs/apk/fdroid/debug/ttrssreader-fdroid-debug.apk`
  - `downloaded-apk/run-39129627378/ttrssreader/build/outputs/apk/play/debug/ttrssreader-play-debug.apk`
- No local Android build or tests were run, in accordance with repository instructions.
- Manual device testing: not yet available. Do not merge without passed manual tests and explicit user approval.
