# Configurable HTTP timeout for backend API calls

## Problem
All TT-RSS API calls go through the single `OkHttpClient` in `JSONConnector.initHttpClient()`.
Timeouts are hard-coded: read = 10 s (10 min with "Lazy Server"), connect/write = OkHttp defaults (10 s).
The client is only built in the constructor, so setting changes are not picked up until restart.

## Decisions (confirmed)
- New setting **API timeout (seconds)**, default **10** → applied to connect, read and write timeouts.
- New setting **Lazy server timeout (minutes)**, default **10** → when Lazy Server is enabled it replaces
  the seconds value for connect, read and write.
- Both are numeric text fields (`EditIntegerPreference`) that open the **numeric keypad**.
- Any positive integer is accepted, no upper bound. 0 / empty / non-numeric is rejected (previous value kept).

## Approach
1. **Constants** (`preferences/Constants.java`)
   - `API_TIMEOUT = "ConnectionApiTimeoutPreference"`, `API_TIMEOUT_DEFAULT = 10`
   - `LAZY_SERVER_TIMEOUT = "ConnectionLazyServerTimeoutPreference"`, `LAZY_SERVER_TIMEOUT_DEFAULT = 10`
   - Names follow the `X` / `X_DEFAULT` convention so `getDefaultValueInt()` and `resetPreferences()` work.
2. **Controller** (`controllers/Controller.java`)
   - `apiTimeoutSeconds()` and `lazyServerTimeoutMinutes()` read via `prefs.getInt`, honoring per-Wi-Fi
     keys with `getStringWithSSID(...)` exactly like `proxyPort()`. Non-positive stored values fall back to default.
3. **Preferences UI** (`res/xml/prefs_connection.xml`)
   - Add `EditIntegerPreference` for API timeout (after the URL/credentials block, near Lazy Server).
   - Add `EditIntegerPreference` for Lazy server timeout right after the Lazy Server checkbox, with
     `android:dependency="ConnectionLazyServerPreference"` (greyed out when Lazy Server is off).
   - Summaries show the unit (seconds / minutes).
4. **Numeric keypad** (`preferences/fragments/ConnectionPreferencesFragment.java`)
   - `setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER))` for both
     (androidx `EditTextPreference` ignores `android:inputType` in XML, so this is required for the numpad).
   - `setOnPreferenceChangeListener` rejecting values that are not a positive integer.
   - Also covers the per-Wi-Fi connection screen, since it reuses this fragment/XML (verify keys get SSID-suffixed
     by `WifiPreferencesFragment.tryInitializeWifiPrefs`).
5. **JSONConnector** (`net/JSONConnector.java`)
   - Compute effective timeout: lazy ? minutes × 60 s : seconds; clamp to `Integer.MAX_VALUE` ms
     (OkHttp throws `IllegalArgumentException` above that) — required because there is no upper bound.
   - `clientBuilder.connectTimeout/readTimeout/writeTimeout(effective, SECONDS)`.
   - Include both timeout values in the `newSettings` change-detection string and update the Javadoc.
   - Call `initHttpClient()` at the start of `doRequest()` so changed settings take effect without restart
     (cheap string compare; rebuilds client only when settings change). Make it `synchronized` for thread safety.
   - Extract the timeout calculation into a small package-private static helper for unit testing.
6. **Strings**: titles/summaries in `values/strings.xml` and `values-de/strings.xml` (others fall back to English).
7. **Tests**: unit test for the timeout helper (default, lazy conversion, clamping, non-positive fallback).
   Run existing `JSONConnectorTimeoutTest` / `RequestDiagnosticsTest` and the unit test task.
8. **Docs**: update README ("API timeout diagnostics" section currently says timeout durations are unchanged;
   "Automatic synchronization" note) to describe the new settings.

9. **Plan copy**: save this plan to `Plans/configurable api http timeout.md`.
10. **CI**: commit, push, run the GitHub Actions build, check result; fix and re-run if it fails.
11. **Artifacts**: download built APKs into `downloaded-apk/` (same layout as existing runs).

## Notes
- Image caching (`ImageCacher`, 2 s/1 s), `Utils.download()` and the version-check request are not backend API
  calls and stay unchanged.
- No overall call timeout is introduced (unchanged behavior).
