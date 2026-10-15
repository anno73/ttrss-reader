# Fix installation alongside the original TT-RSS Reader

## Problem and approach

The fork could not install on a Galaxy S24 Ultra running Android 16 with the
original reader already installed. Both apps declared the internal content
provider authority `org.ttrssreader`, although this fork has application ID
`org.ttrssreader.anno73`. Android requires unique provider authorities.
The expected installer diagnostic is `INSTALL_FAILED_CONFLICTING_PROVIDER`;
the exact device error has not yet been captured.

Use `org.ttrssreader.anno73.listprovider` for this fork, derived from its
application ID. Preserve side-by-side installation, existing app IDs, signing,
data, provider paths, and both APK flavors. Do not retain the conflicting
authority as an alias or require uninstalling the original.

## Implementation

1. Created branch `fix/side-by-side-provider-authority` from `master`.
2. Changed the [manifest](../ttrssreader/src/main/AndroidManifest.xml) list
   provider authority to `${applicationId}.listprovider`.
3. Enabled BuildConfig generation in [Gradle](../ttrssreader/build.gradle).
   Updated [ListContentProvider](../ttrssreader/src/main/java/org/ttrssreader/model/ListContentProvider.java)
   to use `BuildConfig.APPLICATION_ID + ".listprovider"` for its three shared
   content URIs and UriMatcher. The namespace remains `org.ttrssreader`.
4. Added [CI](../.github/workflows/android-build.yml) checks of both built APKs:
   expected package ID and list-provider component/authority, no legacy
   authority, and no unresolved application-ID placeholder. These checks run
   before upload alongside the unchanged signing verification.
5. Updated [installation documentation](../README.md) to distinguish this
   collision from update signing failures and explain side-by-side support.

## Validation and delivery

- Editor diagnostics: no reported errors in the changed Gradle, Java, manifest,
  or workflow files.
- Patch whitespace checked with CRLF-aware Git settings because existing source
  files are committed with CRLF line endings.
- No local Android build attempted, per repository instructions.
- GitHub CLI authentication was recovered using the existing Git credential
  manager without printing or persisting credentials.
- First CI run `38823168896` compiled both APK flavors and passed the existing
  targeted unit tests, but the added manifest parser rejected the provider
  declaration. Its attribute matching expected only the abbreviated Android
  namespace. Updated matching to also accept the full Android namespace URI
  emitted by aapt2, and added provider output to CI diagnostics. APK publishing
  remained blocked by the failed check. Follow-up CI validation is pending.

## Manual acceptance and merge gate

Pending testing on the affected phone:

1. Install the corrected APK while leaving the original reader installed.
2. Open both apps; confirm the original retains its settings/data.
3. In the fork, verify category, feed, and headline/article browsing.
4. If an existing fork installation is available, test a same-key in-place
   update and retained settings/data without uninstalling.
5. If installation fails, capture the Package Manager error before assuming
   the cause or suggesting any uninstall/data loss.

No device connection is available in this session. Source and APK checks cannot
substitute for device installation. Do not merge into `master` until manual
tests pass and the user explicitly approves the merge.
