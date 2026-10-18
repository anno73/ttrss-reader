# TTRSS-Reader

This is based on TTRSS-Reader fork from [Nils Braden](https://github.com/nilsbraden/ttrss-reader-fork).

It is a client application for the RSS-Reader [Tiny Tiny RSS](http://tt-rss.org/), a PHP-based online feedreader which runs on your own webspace. It is accessible with any browser and even supports keyboard shortcuts through javascript.

This work in progress is heavy AI slop and possibly a mess. It should work as I use it for myself, but don't rely on it.
The main driver for the fork were heavy timeouts in the app against my tt-rss instance and several features I miss during daily use.

# Download

# Agent access to a tt-rss backend

The [tt-rss backend skill](.github/skills/ttrss-backend/SKILL.md) supports reading
feeds/articles, explicit read/star changes, and user-authorized RSS subscriptions.
It uses Windows PowerShell 5.1 without external modules. Copy
[ttrss.config.json.example](ttrss.config.json.example) to `ttrss.config.json`
and [.env.example](.env.example) to `.env`, then configure them privately.
Both live configuration files are Git-ignored. Never commit credentials or
private endpoint URLs. See the skill for supported commands and safety rules.

# Installing and updating GitHub Actions APKs

## Build versions

The Android version name is `<normalized-branch>_yyyy-MM-dd_HH-mm-ss`, using
the build time in **UTC**, for example `master_2026-10-18_15-46-18`.
Characters outside letters, digits, `.`, `-` and `_` in the source branch
are replaced individually with `_`: `feature/some-feature-branch-name`
becomes `feature_some-feature-branch-name`. Pull-request builds use the
source branch, not the temporary merge ref.

**Preferences > Information > Version** displays the full installed version
as a read-only row. The About screen uses the same installed package metadata.
Installing another build no longer opens an in-app release-notes screen.
The welcome screen is shown only on the first launch of a fresh installation.
Repository changelogs and external project/download links remain available.
The legacy email crash reporter, its preferences, and its background request
to the original developer's minimum-supported-version endpoint have been removed.

The numeric Android `versionCode` is whole seconds since **2020-01-01 00:00:00
UTC**, derived from the same build instant. Builds fail if the code is not
greater than the previous fixed code `1982` or exceeds Google Play's limit of
`2100000000` (reached in 2086). The full `yyyyMMddHHmmss` date would not fit
Android's standard integer version code.

CI generates one timestamp for the build job and shares it across Gradle
invocations and both flavors. Outside CI, Gradle reads the Git branch and
current UTC time. For detached checkouts, explicitly set `APP_BUILD_BRANCH`.
Optionally set `APP_BUILD_TIMESTAMP` to a canonical UTC instant such as
`2026-10-18T15:46:18Z` to reuse a build instant across invocations. Missing
branch information or malformed timestamps fail the build rather than
silently producing misleading metadata.

Version codes follow build time, not commit ancestry or completion order,
and depend on an accurate build clock. Normal Android updates reject an older
APK's lower version code, including across branches; rebuild the desired
branch later instead. Same-second builds can share a code (and, on the same
branch, a name), so this is not a uniqueness guarantee for Play releases.
Application IDs and signing requirements are unchanged.

## APK signing

The **Android CI** workflow builds Play and F-Droid debug APKs for this fork
(`org.ttrssreader.anno73`). In-place Android updates require the same signing
key on every build. Fresh runners otherwise generate different debug keys,
causing "App not installed" when replacing an existing installation.

## Configure persistent debug signing

Generate a dedicated key **once**, outside the repository, using the JDK:

```powershell
keytool -genkeypair -keystore "$env:USERPROFILE\ttrss-reader-debug.keystore" -storetype JKS -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

If a keystore has already been provided for this fix, use that file instead;
do not generate another key. Keep a secure backup. These standard debug
passwords are not a security boundary: protect the private keystore itself.
This is debug signing, not a production release-signing setup.

To upload it using an authenticated GitHub CLI without printing key material:

```powershell
$key = "$env:USERPROFILE\ttrss-reader-debug.keystore" # Or the supplied keystore's path
[Convert]::ToBase64String([IO.File]::ReadAllBytes($key)) | gh secret set ANDROID_DEBUG_KEYSTORE_BASE64 --repo anno73/ttrss-reader
```

Alternatively, copy its base64 representation to your local clipboard:

```powershell
Set-Clipboard -Value ([Convert]::ToBase64String([IO.File]::ReadAllBytes($key)))
```

In GitHub, open **Settings > Secrets and variables > Actions > New repository
secret**, name it `ANDROID_DEBUG_KEYSTORE_BASE64`, and paste the clipboard
contents as its value. Replace the clipboard afterwards with `Set-Clipboard
-Value " "` (a single space). Never commit the keystore/base64 or paste it into
issues or chat.

CI explicitly sets `ANDROID_USER_HOME` and restores its `debug.keystore`
before Gradle runs, validates private-key access, and
verifies both APKs against its certificate before publishing. If the secret
is missing, tests still run but APK build/upload are skipped with a notice.
An invalid secret fails the job. Existing `LOCALE_PLUGIN_MAVEN_USER` and
`LOCALE_PLUGIN_MAVEN_PASSWORD` secrets are still needed for private dependencies.

Push a change or select **Actions > Android CI > Run workflow** to compile on
GitHub. Download `ttrss-reader-debug` from the successful run's **Artifacts**,
extract it, and install the desired flavor's APK. Both flavors share an app ID;
installing one replaces the other, rather than installing a second app.

## Installing alongside the original reader

This fork can be installed alongside the original TT-RSS Reader. It uses app ID
`org.ttrssreader.anno73` and internal list-provider authority
`org.ttrssreader.anno73.listprovider`; each app keeps its own settings and data.

Earlier fork APKs retained the original reader's `org.ttrssreader` provider
authority. Android requires provider authorities to be unique, even for providers
that are not exported. If the original reader is installed, those APKs can fail
with "App not installed" (`INSTALL_FAILED_CONFLICTING_PROVIDER` in installer
diagnostics), even after selecting "Install anyway". Use a corrected build;
uninstalling the original reader is not required.

This is separate from signing-key mismatches when updating an existing fork
installation. The provider fix does not change the fork's app ID or signing key;
an existing fork installation can still be updated without clearing data when
the signer matches and the version code is not lower. If installation still
fails, capture the Package Manager error using `adb install -r <apk>` or device
installer logs rather than assuming that uninstalling an app is necessary.

## Device-verifier installation failures

On some devices the installer displays only "App not installed" when a device
verifier rejects an installation. `INSTALL_FAILED_VERIFICATION_FAILURE` (-22)
is different from an incompatible signing key, a version downgrade, or a
conflicting provider. Changing the signing key or uninstalling the existing app
is not an appropriate first response.

Use the APK extracted from the verified Actions artifact and capture the actual
Package Manager result. With platform-tools already installed and USB debugging
authorized, target the intended device explicitly:

```powershell
adb -s DEVICE_SERIAL install -r "C:\path\to\ttrssreader-play-debug.apk"
adb -s DEVICE_SERIAL shell dumpsys package org.ttrssreader.anno73
```

`-r` requests an in-place update; do not add an uninstall, data-clear, or downgrade
step. Confirm the installed version and existing settings/data afterward. A
successful ADB installation does not prove the normal installer path is fixed:
verifiers can treat installation sources differently. Inspect the device's
verification notifications/logs for a source-specific rejection; do not disable
device protection or rotate the persistent signing key to hide the error.

## Migrating an existing installation

An APK signed by an old ephemeral CI key cannot be updated with the new key.
Recovering that original private keystore is the only normal way to preserve
in-place updates from that installation; its public certificate in the APK
cannot reconstruct the private key.

If the original key is unavailable, export/back up any supported settings and
data first, then uninstall the old app and install the first consistently
signed build. **Uninstalling can delete local app data**, including cached or
unsynchronized data; do not assume it can all be exported or restored.
Subsequent APKs signed with this same persistent key can update the app without
uninstalling. Never replace the signing secret with a newly generated key.

To verify, build twice in separate Actions runs, confirm both runs report the
same signer SHA-256, and install the second APK over the first on Android.
Check that installation succeeds and existing settings/data remain intact.
Do not lower `versionCode` when updating; version downgrades are a separate
installation failure from a signer mismatch.

# Features
 * Use Swipe for Next/Previous Article
 * Swipe-Area in bottom area of the Article-View to avoid swiping while scrolling
 * Show or download images/videos/music attached to Articles
 * Save article images and videos to a folder selected with the system folder picker
 * Share URL via Twitter/SMS/Mail
 * Mark everything (whole category or all categories) as read
 * Navigate through articles with volume-buttons
 * Read Labels
 * Cache articles in local-database
 * Cache images for offline browsing
 * SSL-Supoprt with self-signed certificates
 * Scheduled updates via Tasker/Locale
 * Fast JSON-Parsing with stream-based parser GSON 

# Saving article media
When saving an article image or video through its long-press menu, choose a folder
in the system picker on the first save. The app remembers that folder across
restarts. Files are saved directly into it with their original name and format;
duplicate names get a numbered suffix rather than overwriting existing files.
To use `Pictures/tt-rss`, create or select that folder in the picker.

Change the destination under **Settings > Usage > Media save folder**.
Optional triple-tap saving uses the same folder and is disabled by default.
Cancelling the picker cancels the pending save. If folder access is lost, the next
save explains the problem and asks for a folder again. No broad storage permission
is needed for this feature; Android still restricts which folders can be selected.
The separate attachment-download location setting is unchanged.

# Automatic synchronization
Under **Settings > System**, **Sync on startup and resume** is enabled by default.
Disable it to skip automatic category/feed overview synchronization when opening or
resuming the reader, and to prevent automatic startup image caching. Cached data
remains available; use **Refresh** to synchronize manually.

Manual image caching, article fetching while browsing, and Tasker/Locale updates
are unchanged. Disabling the setting does not cancel work already running or
change backend request timeouts.

# API timeouts
Under **Settings > Connection** two numeric settings control the HTTP timeout of
all backend API calls (connect, read and write):
 * **API timeout (seconds)**, default 10.
 * **Lazy server timeout (minutes)**, default 10. Used instead of the seconds
   value while **Lazy Server** is enabled.

Any positive whole number is accepted; both can also be set per Wi-Fi network.
Changes apply to the next API request without restarting the app. Image and
attachment downloads are not affected.

# API page size
Under **Settings > Connection**, **Maximum items per API request** defaults to
**200**, matching the previous headline page size. Enter a whole number from 1 to
2147483647; invalid input is rejected without changing the saved value. Per-Wi-Fi
overrides are supported, and changes apply to the next download without restarting.

This controls each page, not the total download. Headlines are additionally capped
at 200 per request (60 on API levels below 6) and the remaining download budget.
Real-feed requests use the configured size with `limit`/`offset`; that endpoint
has no documented hard cap, so very large settings can still produce large responses.
Labels and virtual feeds are non-pageable metadata: they are retained once but
can make a response larger than the configured real-feed limit. Categories are
not pageable and remain unchanged, as do article-state update batches and image
downloads. Pagination follows the upstream
[API handlers](https://github.com/tt-rss/tt-rss/blob/HEAD/classes/API.php).

# API timeout diagnostics
Backend API timeout dialogs and logs include the called endpoint (`URL:`) and
the JSON request body (`JSON:`), including timeouts while reading a response.
For requests targeting a specific feed, diagnostics also show the locally stored
feed name and subscribed source URL (`Feed name:` and `Feed URL:`); category
requests are not attributed to a feed. Credentials and recognized token query
parameters in the feed URL are redacted using the same rules as other diagnostic
URLs.
Credentials and authentication/session tokens are replaced with
`VALUE_REDACTED`; the actual request is not modified. This includes login
usernames/passwords, `sid`, embedded URL credentials, and known credential/token
query parameters in the endpoint or body URL fields. Authorization headers are
never included.

All other parameters remain visible, including search text, article IDs, notes,
titles, article content, and ordinary URLs. Review diagnostics before sharing
them. Custom credential parameter names and secrets embedded in paths or
free-text fields are not automatically detected. Image/resource downloads and
retries are unchanged; see **API timeouts** for configuring timeout durations.

# Changelog

# Contact

# Screenshots
![Screenshot 1](http://nilsbraden.de/android/screenshots/small/device-2014-01-02-163718_1.png)
![Screenshot 2](http://nilsbraden.de/android/screenshots/small/device-2014-01-02-163709_1.png)
![Screenshot 3](http://nilsbraden.de/android/screenshots/small/device-2014-01-02-163755_1.png)
![Screenshot 4](http://nilsbraden.de/android/screenshots/small/device-2014-01-02-163740_1.png)
![Screenshot 5](http://nilsbraden.de/android/screenshots/small/Screenshot_2014-01-02-16-34-03_1.png)
![Screenshot 6](http://nilsbraden.de/android/screenshots/small/Screenshot_2014-01-02-16-34-16_1.png)
![Screenshot 7](http://nilsbraden.de/android/screenshots/small/Screenshot_2014-01-02-16-34-39_1.png)
![Screenshot 8](http://nilsbraden.de/android/screenshots/small/Screenshot_2014-01-02-16-35-11_1.png)
![Screenshot 9](http://nilsbraden.de/android/screenshots/small/Screenshot_2014-01-02-16-35-30_1.png)
