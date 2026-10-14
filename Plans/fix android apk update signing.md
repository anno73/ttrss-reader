# Fix Android APK update installation

## Problem and evidence

Initial manual APK installation works; installing a later build reports
"App not installed". JDK `keytool -printcert -jarfile` shows different Play
debug APK signer SHA-256 fingerprints:

- Run 38645691222:
  `89:46:FB:4C:8C:79:40:35:AD:19:A8:C8:C8:84:72:1A:8F:D5:B8:9C:D6:4C:22:4C:06:7E:05:BA:B0:18:22:8F`
- Run 38649090156:
  `9B:F8:72:3C:58:57:C6:82:D3:BC:EA:2A:E8:23:C9:E2:4C:D1:1F:FE:72:6D:57:7F:EB:63:D4:2F:EF:1D:B0:6D`

The [CI workflow](../.github/workflows/android-build.yml) builds debug APKs
on fresh runners without a persistent signing key. Android rejects a normal
update signed by a different key. The exact installed APK and device Package
Manager error have not been inspected.

## Approach and scope

Keep both debug flavors and the current application ID. Restore a persistent
debug keystore from `ANDROID_DEBUG_KEYSTORE_BASE64` before any Gradle task.
When the secret is absent, run existing tests but skip APK build/upload with
a notice. Malformed or unusable keys fail explicitly.

Do not change application behavior, versioning, release signing, or existing
private Maven credential handling. Do not install a local Android SDK.

## Implementation todos

1. Explicitly set `ANDROID_USER_HOME` and restore the secret into its debug
   keystore location with
   restrictive permissions. Validate private-key access using `keytool`.
   Gate APK building/uploading on successful signing preparation.
2. Verify each expected Play/F-Droid APK using CI's `apksigner`, requiring
   exactly one signer matching the restored certificate. Always remove the
   restored key after the job; upload only APK artifacts.
3. Generate a persistent debug keystore outside the repository. Never commit
   it or print its private contents. Document secure backup and repository
   secret installation in the [README](../README.md).
4. Validate signing preparation for valid, absent, malformed, and unusable
   keys with existing JDK tools. Compile on GitHub Actions after installing
   the secret and publishing the workflow. Compare two independent CI runs.
5. Verify an Android in-place update from the first consistently signed build
   to the second, including preservation of settings/data.

Signer verification depends on signing preparation; documentation depends on
final workflow/secret names; final validation depends on implementation and
documentation.

## Migration and validation considerations

Unless the original private CI key is recovered, a one-time uninstall/reinstall
is necessary. Back up supported settings/data first; uninstalling can remove
cached and unsynchronized data, not all of which may be exportable. A public
certificate in an APK cannot recover the private signing key.

GitHub secret provisioning and workflow publication require authenticated
repository access. Report access blockers explicitly. Device testing requires
device access and must not be claimed from certificate inspection alone.

## CI validation finding

The first validation builds compiled and passed tests, but artifact checks
correctly rejected a different signer. Gradle's signing report showed that
AGP resolves the runner's default debug key under
`/home/runner/.config/.android`, not `/home/runner/.android`. Explicitly setting
`ANDROID_USER_HOME` makes restoration, Gradle signing, and cleanup use the same
directory, independent of the runner's XDG configuration.
