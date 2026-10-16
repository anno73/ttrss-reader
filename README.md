# TTRSS-Reader
TTRSS-Reader (or ttrss-reader-fork) is a fork of the original Project from [ttrss-reader](https://code.google.com/p/ttrss-reader/). I took the sourcecode and continued the development because the original owner didn't reply to any messages back then (and has not done so as of today).

It is a client application for the RSS-Reader [Tiny Tiny RSS](http://tt-rss.org/), a PHP-based online feedreader which runs on your own webspace. It is accessible with any browser and even supports keyboard shortcuts through javascript.
Download

Since google announced that the downloads from google-code will not be available after January 14th, 2014 I started to move all downloads to google-drive. The commentary with the current changelog will be removed in the process but the full changelog can be viewed in the Wiki.

[![play.google.com][3]][4]<br>
[![QR-Code for play.google.com][5]][4]

 * [**Download current Version**](https://github.com/nilsbraden/ttrss-reader-fork/releases)
 * ([**Changelog**](https://github.com/nilsbraden/ttrss-reader-fork/wiki/Changelog))

# Agent access to a tt-rss backend

The [tt-rss backend skill](.github/skills/ttrss-backend/SKILL.md) supports reading
feeds/articles, explicit read/star changes, and user-authorized RSS subscriptions.
It uses Windows PowerShell 5.1 without external modules. Copy
[ttrss.config.json.example](ttrss.config.json.example) to `ttrss.config.json`
and [.env.example](.env.example) to `.env`, then configure them privately.
Both live configuration files are Git-ignored. Never commit credentials or
private endpoint URLs. See the skill for supported commands and safety rules.

# Installing and updating GitHub Actions APKs

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

See [Changelog](https://github.com/nilsbraden/ttrss-reader-fork/wiki/Changelog) for detailed information.

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

# Note regarding support and F-Droid
Since [F-Droid](https://f-droid.org/repository/browse/?fdid=org.ttrssreader) is often several months late with the build I have to make this clear: I won't provide support for versions which are not exactly or at least near to the version which is stated above on this site ("Current Version"). Please provide the full version information from the About-box within the app when reporting an issue!

# Changelog
Please follow this [link](https://github.com/nilsbraden/ttrss-reader-fork/wiki/Changelog)

# Contact
Please feel free to contact me via E-Mail (ttrss /at/ nilsbraden.de) or via the Bugtracker (Issues). If you have questions specifically about Tiny Tiny RSS you may ask me or visit the [Tiny-Tiny-RSS-Forum](http://tt-rss.org/forum/) or #tt-rss at irc.tt-rss.org. If I don't answer fast enough or am not willing to implement your suggestion please keep in mind that this is only a hobby for me and I'm unwilling to implement every little feature just beacuse you suggested I do so. You may implement it and send me pull-requests so I can include the features but I can't guarantee I will do this for every thing you want to have.

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

[3]: https://www.nilsbraden.de/android/tt-rss/google-play-badge.png
[4]: https://play.google.com/store/apps/details?id=org.ttrssreader
[5]: https://www.nilsbraden.de/android/tt-rss/qrcode.png
