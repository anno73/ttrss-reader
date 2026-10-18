# Show media save folder preference path

## Scope

- Show the selected media folder as its full user-visible path in Usage preferences.
- Include the storage root label shown by the system folder picker, such as `Internal storage/Pictures/ttrss`.
- Omit opaque volume identifiers and allow the complete summary to wrap instead of truncating.
- Leave folder selection, persisted access grants, and media saving behavior unchanged.

## Implementation

- Use `DocumentsContract.findDocumentPath` to render ancestor display names for providers that expose document ancestry.
- For the External Storage Provider, reconstruct the relative path from the tree document ID and obtain the visible volume title from Android storage-volume metadata.
- Do not query provider-global root metadata: the persisted SAF grant covers the selected tree, not the provider's `/root` listing.
- For the standard External Storage Provider, prefer the tree document ID path even on newer Android versions; a non-null but incomplete `findDocumentPath` result must not prevent that reconstruction.
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

## Follow-up investigation

- The user reported that the installed build still showed only the selected folder's last component.
- The path formatter accepted any non-null `DocumentsContract.findDocumentPath` result before attempting External Storage Provider reconstruction. This allowed a non-null but incomplete provider path to bypass the reliable `primary:Pictures/ttrss` tree-ID path.
- The formatter now prioritizes the External Storage Provider volume-relative document ID on all supported Android versions. Other providers continue to use `findDocumentPath` where available.
- Added a regression test through the public formatter used by the preference, expecting the root title and full relative folder path.
- An initial follow-up CI run caught a missing document-URI stub in that regression test. The fixture was corrected; no production change was needed for that failure.
- GitHub Actions run `39135425337` passed the regression and existing unit tests, both APK builds, package/provider identity checks, and signing verification.
- Downloaded the run's artifact to `downloaded-apk/run-39135425337/ttrss-reader-debug.zip`, extracted it into the same directory, and verified SHA-256 `5a90b2e7c7baae7e58ec5a9a9f6703c4427d1fb9330c6fe3f8ae0bcebefc4860`. Confirmed the fdroid and play APKs exist under `ttrssreader/build/outputs/apk/{fdroid,play}/debug/`.
- Manual device testing remains outstanding. Install this updated APK and confirm the preference shows the full visible path, e.g. `Internal storage/Pictures/ttrss`, and wraps long paths. Do not merge before successful manual tests and explicit user approval.

## Permission-denial follow-up

- The user reported that the preference now surfaced a `SecurityException` for `content://com.android.externalstorage.documents/root`. The formatter queried this provider-global root endpoint to obtain the root title, which is not covered by a persisted tree grant.
- Removed root-table lookups. External-storage volume labels now come from `StorageManager`/`StorageVolume`; selected-tree-relative folder components continue to come from the tree URI's document ID. For other document providers, the root entry is omitted and the authorized ancestry entries are formatted without querying global roots.
- Added regression coverage for the external-storage title and path, and assertions that neither external-storage nor generic providers are queried for root listings.
- GitHub Actions run [39136488622](https://github.com/anno73/ttrss-reader/actions/runs/39136488622) passed the media-saving and network unit tests, both debug APK builds, package/provider identity checks, and signing verification.
- Downloaded the run's artifact to `downloaded-apk/run-39136488622/ttrss-reader-debug.zip` and extracted it into the same directory. Verified SHA-256 `3d4eee6dce9723d94bb48a228b447ba12b490c5d7bebb008247ea9fbfbade4fe` and confirmed the fdroid and play APKs exist under `ttrssreader/build/outputs/apk/{fdroid,play}/debug/`.
- No local Android build or tests were run, in accordance with repository instructions.
- Manual device testing is still required to confirm the permission error is gone and the preference displays the complete path. Do not merge without successful manual tests and explicit user approval.
