# F-Droid vs Play APK flavours

**Research date:** 2026-10-19

## Executive recommendation

For ordinary sideloading of this fork on a reasonably current Android device,
prefer the **F-Droid flavour**. It offers the same reader features, omits the
Google Play services client integration, and is smaller. The Play flavour's
intended advantage is an optional Google Play services security-provider
update, but the current startup order means that the app's main API TLS client
is initialized before that update is requested. The Play APK should therefore
not be assumed to provide stronger TLS for the tt-rss API connection.

This is a recommendation about the **flavour**, not the distribution channel.
Either flavour can be sideloaded; the `fdroid` name does not establish that
F-Droid built, reviewed, or signed a particular APK.

## Scope and evidence

This analysis examined the Gradle flavour definitions and dependencies,
flavour-specific source files, shared application startup and TLS code,
preferences, network-security configuration, and both APKs from successful
[GitHub Actions run 39165263256](https://github.com/anno73/ttrss-reader/actions/runs/39165263256).
The APK comparison used the archived debug APKs produced by that run. Android,
Google, and F-Droid documentation is linked in the references below.

The CI workflow tests selected unit tests using the F-Droid debug variant and
builds both debug variants. It checks the two APKs' version, package/provider
identity, and signing identity. Those checks do not test Google provider
installation on physical devices. No device TLS handshake, network-traffic,
battery, or performance measurements were made.

## Do not confuse flavour, build type, and distribution

These are separate concepts:

| Term | Meaning in this project |
|---|---|
| **Product flavour** | `fdroid` or `play`, selecting dependency and source-set differences. |
| **Build type** | `debug` or `release`, selecting development or release build settings. |
| **Distribution channel** | GitHub Actions, F-Droid, Google Play, or manual APK transfer. |

Android combines product flavours and build types to produce build variants.
Thus a Play-flavoured APK can be sideloaded, and an F-Droid-flavoured APK does
not require the F-Droid client. The flavour name alone does not say who built,
verified, signed, or distributes the APK.

The APKs compared here are both **debug builds from this repository's GitHub
Actions workflow**, not official releases from the two stores.

## What differs in this repository

The flavours are defined in [`ttrssreader/build.gradle`](ttrssreader/build.gradle):

```groovy
productFlavors {
    play {
        dimension "version"
    }
    fdroid {
        dimension "version"
    }
}
```

Both use the same application ID, minimum SDK, target SDK, shared resources,
and main source tree. The meaningful dependency distinction is:

```groovy
playImplementation 'com.google.android.gms:play-services-base:18.10.0'
```

### F-Droid source set

[`ttrssreader/src/fdroid/java/org/ttrssreader/gui/MenuFlavorActivity.java`](ttrssreader/src/fdroid/java/org/ttrssreader/gui/MenuFlavorActivity.java)
provides an ordinary `AppCompatActivity` base. It does not call the Google
security-provider installer.

### Play source set

[`ttrssreader/src/play/java/org/ttrssreader/gui/MenuFlavorActivity.java`](ttrssreader/src/play/java/org/ttrssreader/gui/MenuFlavorActivity.java)
uses `ProviderInstaller.installIfNeededAsync()`. If installation fails, it can
offer a Google Play services recovery dialog or show an ignorable warning.
Several activities inherit from this base.

I found no corresponding flavour split for feed/article handling, tt-rss API
operations, read/star state, feed subscription, image caching/saving,
background reader work, or Tasker/Locale integration. The functional
difference is the provider-installer integration, not a different set of
reader features.

## APK comparison

The compared artifacts are:

- [`ttrssreader-fdroid-debug.apk`](downloaded-apk/run-39165263256/ttrssreader/build/outputs/apk/fdroid/debug/ttrssreader-fdroid-debug.apk)
- [`ttrssreader-play-debug.apk`](downloaded-apk/run-39165263256/ttrssreader/build/outputs/apk/play/debug/ttrssreader-play-debug.apk)

| Property | F-Droid flavour | Play flavour |
|---|---:|---:|
| APK size | 11,994,734 bytes | 12,520,184 bytes |
| Approximate size | 11.44 MiB | 11.94 MiB |
| Application ID | `org.ttrssreader.anno73` | Same |
| Version name | `master_2026-10-18_20-52-13` | Same |
| Version code | `214519933` | Same |
| Minimum Android version | Android 6 / API 23 | Same |
| Target SDK | 36 | Same |
| Debuggable | Yes | Yes |
| Declared permissions | Same | Same |
| Google Play services class definitions | 0 | 819 |
| ProviderInstaller integration | Absent | Present |

The Play APK is **525,450 bytes larger** (about **513 KiB**, or **4.38%** of
the F-Droid APK's size). Its DEX files and packaged metadata contain Google
Play services base, basement, and tasks components. Its merged manifest also
has a non-exported `GoogleApiActivity` and Play services version metadata.

I found no extra Android permissions in the Play manifest. Three
Firebase-namespaced exception/helper classes are present in its DEX files;
that alone is **not** evidence that Firebase Analytics or Crashlytics is
integrated.

The size comparison is for **debug APKs**. Release builds enable minification
and resource shrinking, so the exact release size difference may differ.
File size alone does not demonstrate a battery, speed, or runtime-performance
difference.

## Intended security benefit of the Play flavour

Android's Java security and TLS APIs use security providers. Google Play
services' `ProviderInstaller` can update a device's security provider without
waiting for an OEM firmware update. Its potential benefits are quicker access
to some cryptographic fixes and compatibility improvements, especially on
older supported devices.

The update requires compatible, functioning Google Play services on the device.
It is not an OS or WebView update, does not encrypt cleartext HTTP, does not
repair every networking stack, and does not replace certificate or hostname
validation.

Google's guidance for `installIfNeededAsync()` says applications should wait
for its success callback before attempting secure communication. This is
important to the interpretation of this project's implementation.

## Important implementation limitation: initialization order

The shared startup path is, in outline:

```text
MyApplication.onCreate()
  -> Controller.initialize()
     -> SSLUtils creates and stores an SSLContext socket factory

Later:
  Play MenuFlavorActivity.onCreate()
     -> starts ProviderInstaller asynchronously
        -> success callback only logs success
```

The relevant implementation is in:

- [`MyApplication.java`](ttrssreader/src/main/java/org/ttrssreader/MyApplication.java)
- [`Controller.java`](ttrssreader/src/main/java/org/ttrssreader/controllers/Controller.java)
- [`SSLUtils.java`](ttrssreader/src/main/java/org/ttrssreader/utils/SSLUtils.java)
- [`MenuFlavorActivity.java` (Play)](ttrssreader/src/play/java/org/ttrssreader/gui/MenuFlavorActivity.java)
- [`JSONConnector.java`](ttrssreader/src/main/java/org/ttrssreader/net/JSONConnector.java)

`Controller.initialize()` creates an `SSLContext` and saves its socket factory
in `SSLUtils.factory`. `JSONConnector` later supplies that existing factory to
OkHttp. The Play activity requests provider installation after application
startup; its success callback only logs and does not rebuild the `SSLContext`,
socket factory, or API client. Nor does it gate background API calls on
installation completion.

An `SSLContext` and its socket factory represent an initialized TLS
implementation, so changing the provider registry later is not the same as
reconstructing the app's previously created TLS objects. The source therefore
does **not establish that the existing tt-rss API client will use the newly
installed provider**. Other security objects created after installation might
benefit, and the exact behaviour should be verified on a device before making
a stronger claim.

The defensible conclusion is: provider updating is the Play flavour's intended
advantage, but its benefit to this app's primary API TLS path is uncertain
because of the initialization order. The callback's success log alone does
not prove that the connector uses the updated provider.

## F-Droid is not inherently less secure

The F-Droid flavour uses Android's ordinary security/TLS APIs and does not
disable TLS. Current Android versions include Conscrypt, the platform TLS and
cryptography implementation. Android's modular system supports Conscrypt
updates independently of full OS upgrades on supported devices; Android 14
also introduced an updatable root trust store within Conscrypt.

This does not mean every old or unmaintained device has current TLS or trust
roots. Device maintenance remains important. A separately updated provider
could be more useful on older devices, but only when Google Play services is
available and the application actually uses the updated provider.

Both flavours share
[`network_security_config.xml`](ttrssreader/src/main/res/xml/network_security_config.xml),
which permits cleartext traffic and trusts system and user-installed
certificates. They also share their custom certificate/trust preferences.
Neither flavour label overrides these settings. For a secure deployment, use
an `https://` tt-rss endpoint with a properly configured certificate; do not
treat trust-all options as an ordinary certificate fix.

## Privacy and dependency implications

The F-Droid APK excludes this app's Google Play services client dependency
and provider-installer calls. That means fewer packaged dependencies, no
provider-installer recovery prompts from this integration, and a better fit
for devices intentionally operated without Google services.

The dependency alone is **not evidence that the app sends feeds, passwords,
reading history, or articles to Google**, nor is it evidence of Analytics or
Crashlytics. Google's current data-disclosure information for the base,
basement, and tasks SDKs says those SDKs do not collect end-user data. This
does not amount to a complete audit of Google Play services at the device
level, and no runtime traffic capture was performed.

F-Droid's inclusion policy requires free-software dependencies and explicitly
allows projects to provide flavours that omit proprietary dependencies. The
existence of this `fdroid` variant does not, by itself, certify that every
project dependency and build step meets all current F-Droid inclusion rules.
Likewise, the local APK used here is a GitHub Actions build, not proof of an
F-Droid-built or reproducibly verified binary.

## Why maintain two flavours for sideloaded APKs?

**Reasons to keep both:**

1. **Different device environments:** users with Google Play services can
   use the provider installer; users without it can install a build without
   that runtime dependency.
2. **Distribution compatibility:** a dependency-free flavour helps projects
   prepare for stores with restrictions on proprietary libraries.
3. **User choice:** users can choose whether to include the integration.
4. **Actual exclusion:** turning off a preference in the Play build does not
   remove its SDK classes or manifest contributions; a separate flavour does.

**Costs:**

- Two binaries and dependency graphs to build and maintain.
- More source paths and failure modes to test.
- Potential confusion about whether a flavour name denotes a distribution
  channel or guarantees additional security.
- In this repository, the Play-specific path needs device testing and a
  review of initialization sequencing to establish its intended benefit.

For a project used only for personal sideloading on current devices, the
practical value of maintaining both flavours is modest unless the Play
provider update is confirmed to help the actual network clients.

## Installation and updates

Both flavours use the same application ID and, in the compared CI artifacts,
the same version and signing identity. They **replace one another**; they are
not designed to be installed side by side. A normal in-place update should
retain the app's data when the signer matches and the incoming version code
is not lower. A signing mismatch, version downgrade, or device installer
policy can still block installation.

Do not assume that an APK installed from GitHub can be replaced by an APK
from an official store without checking application ID, signature, and
version compatibility. F-Droid can publish developer-signed builds when its
reproducibility checks verify them; it is not universally true that an
F-Droid APK must have a different signer.

The compared APKs are debug builds. Choosing the F-Droid flavour does not
make an APK a production release. Both use the repository's debug signing
arrangement and are marked debuggable.

## Recommendation by situation

| Situation | Recommendation | Rationale |
|---|---|---|
| Sideloading on a current, maintained Android device | **F-Droid** | Same reader features, fewer dependencies, and no demonstrated need for the Play integration. |
| Device without Google Play services | **F-Droid** | Avoids an unavailable integration and possible recovery prompts. |
| Preference for free-software dependencies | **F-Droid** | Omits this Google Play services client integration. |
| Old device with working Google Play services | **Conditional** | Provider updating may help, but the current code does not establish that the main API client uses the updated provider. |
| Play flavour already installed and working | **No urgent switch required** | There is no reader-feature difference that requires replacement. |
| Want automatic updates from Google Play | **Choose a distribution channel, not a flavour name** | Sideloading the Play flavour does not provide Play Store delivery or updates. |

There is a UI inconsistency: the shared
[`prefs_ssl.xml`](ttrssreader/src/main/res/xml/prefs_ssl.xml) displays a
“Use Google Play services: Provider-Installer” preference in both variants.
The shared preference fragment does not hide it in the F-Droid build, even
though that flavour's activity implementation has no provider-installer
integration. In the F-Droid variant the checkbox should not be interpreted as
evidence that Google services are present or used.

## Key takeaways

- **For ordinary sideloading on a maintained device, prefer the F-Droid
  flavour.**
- Both flavours have the **same reader features**, application ID,
  permissions, and Android version requirements.
- Play's only substantive extra is the **Google Play services security
  provider installer**.
- The app initializes the API TLS factory **before** starting that
  installation and does not recreate it afterward. Thus, stronger TLS for
  the tt-rss API client is **not established** by this implementation.
- In the inspected debug APKs, Play is about **513 KiB / 4.38% larger**.
  This is a storage observation, not a performance benchmark.
- A Play services dependency is **not, on its own, evidence of analytics or
  feed/account-data collection**.
- `fdroid` and `play` are build flavours, **not proof of who built or
  distributes an APK**.
- The flavours share an app identity and replace each other; they do not
  install side by side.
- A secure, maintained Android device and a correctly configured HTTPS
  endpoint matter more than the APK flavour name.

## References

### Repository sources

- [`ttrssreader/build.gradle`](ttrssreader/build.gradle) — flavours and the
  Play-only dependency.
- [`MenuFlavorActivity.java` (F-Droid)](ttrssreader/src/fdroid/java/org/ttrssreader/gui/MenuFlavorActivity.java)
  and [`MenuFlavorActivity.java` (Play)](ttrssreader/src/play/java/org/ttrssreader/gui/MenuFlavorActivity.java)
  — flavour-specific code.
- [`MyApplication.java`](ttrssreader/src/main/java/org/ttrssreader/MyApplication.java),
  [`Controller.java`](ttrssreader/src/main/java/org/ttrssreader/controllers/Controller.java),
  [`SSLUtils.java`](ttrssreader/src/main/java/org/ttrssreader/utils/SSLUtils.java),
  and [`JSONConnector.java`](ttrssreader/src/main/java/org/ttrssreader/net/JSONConnector.java)
  — startup, TLS context creation, and API client configuration.
- [`network_security_config.xml`](ttrssreader/src/main/res/xml/network_security_config.xml)
  and [`prefs_ssl.xml`](ttrssreader/src/main/res/xml/prefs_ssl.xml) — shared
  network-security and SSL preferences.
- [Android CI workflow](.github/workflows/android-build.yml) — tests, APK
  builds, and identity checks.
- [CI run 39165263256](https://github.com/anno73/ttrss-reader/actions/runs/39165263256)
  — source of the compared artifacts.

### Official documentation

- [Android build variants](https://developer.android.com/build/build-variants)
  — product flavours and build types.
- [Android: Update your security provider](https://developer.android.com/privacy-and-security/security-gms-provider)
  — `ProviderInstaller`, asynchronous completion, and security-provider
  behaviour.
- [Google Play services overview](https://developers.google.com/android/guides/overview)
  and [setup guidance](https://developers.google.com/android/guides/setup)
  — runtime service and device-availability considerations.
- [Android `SSLContext` reference](https://developer.android.com/reference/javax/net/ssl/SSLContext)
  — secure socket protocol contexts.
- [AOSP Conscrypt documentation](https://source.android.com/docs/core/ota/modular-system/conscrypt)
  — Android's modular TLS and cryptography implementation.
- [Google Play services data disclosure](https://developers.google.com/android/guides/play-data-disclosure)
  — data-handling statements for base, basement, and tasks SDKs.
- [F-Droid inclusion policy](https://f-droid.org/docs/Inclusion_Policy/)
  — free-software and dependency policy.
- [F-Droid reproducible builds](https://f-droid.org/docs/Reproducible_Builds/)
  — reproducibility and signature verification.
- [Android app signing](https://developer.android.com/studio/publish/app-signing)
  — signing requirements and update identity.
