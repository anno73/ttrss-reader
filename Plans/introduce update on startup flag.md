# Optional automatic startup synchronization

## Problem and approach

Backend timeouts make automatic feed synchronization disruptive when opening the reader. Add a persistent, global System preference named "Sync on startup and resume", enabled by default. Turning it off skips automatic category/feed overview synchronization and startup image caching, while still displaying cached data.

Scope confirmed with the user:
- Do not change backend timeout values.
- Preserve current behavior unless the user disables the new setting.
- Apply the setting to overview synchronization on launch and subsequent resumes.
- Suppress automatic startup image caching too, because it synchronizes articles, categories, feeds, and pending status changes.
- Keep manual refresh, manually started caching, article fetching while browsing, and Tasker/Locale updates unchanged.
- This is not a global offline mode and does not cancel already-running work.

## Current implementation

- `MenuActivity.onResume()` and preference/error activity results call `refreshAndUpdate()`, which dispatches `doUpdate(false)` and refreshes the local UI.
- `CategoryActivity.doUpdate(boolean)` starts the overview updater; manual refresh passes `true`.
- `CategoryActivity.onCreate()` separately starts image caching, which performs full synchronization through `ImageCacher`.
- Category list loaders read the local database and must remain active when automatic synchronization is disabled.
- `FeedHeadlineActivity` shares the base lifecycle update path, so gating the shared base method would unintentionally disable article fetching when browsing.
- `Controller` caches preferences and invalidates matching fields through the naming convention in `Constants`.
- System settings are global; connection settings also have per-Wifi variants, which are not needed for this feature.
- No existing unit-test or instrumentation-test sources were found.

## Implementation todos

1. Add the preference and controller access.
   - In `preferences/Constants.java`, add `SYNC_ON_STARTUP` with key `SyncOnStartupPreference` and `SYNC_ON_STARTUP_DEFAULT = true`.
   - In `controllers/Controller.java`, add nullable cached field `syncOnStartup` and accessor `syncOnStartup()`, following existing cache/invalidation conventions.
   - In `res/xml/prefs_system.xml`, add the enabled-by-default checkbox ahead of startup image caching.
   - Make the existing startup image-cache checkbox depend on the new setting, preserving its stored value and its existing Wifi-only child dependency.
   - In `res/values/strings.xml`, add the title and a summary explaining launch/resume overview sync, startup caching, cached-data access, and manual refresh. Use Android's default-language fallback for other locales.

2. Gate only the relevant automatic entry points.
   - In `gui/CategoryActivity.java`, return before creating an updater when `forceUpdate` is false and `syncOnStartup()` is false.
   - Require `syncOnStartup()` as well as the existing cache preference before starting the image cacher in `onCreate()`.
   - Do not change `MenuActivity`, `FeedHeadlineActivity`, synchronization internals, timeout configuration, or manual/background entry points.
   - Preserve local UI refresh and loader behavior regardless of the new preference.

3. Document and validate.
   - Add a short section to the root `README.md` describing where to find the setting, its default, what it disables, and the explicitly unaffected behavior.
   - Review the diff and check Java preference names, default values, XML resources, dependencies, and controller invalidation consistency.
   - Run the narrow Android debug build/resource checks for the F-Droid variant, then compile the Play variant to cover shared-code compatibility; use the existing Gradle wrapper and report any environment blockers explicitly.
   - Run existing relevant tests if discovered during implementation; avoid adding a testing framework solely for this setting.
   - On an available emulator/device, verify the behavioral matrix below. If no device is available, explicitly distinguish build/static verification from unexecuted runtime checks.

## Behavioral verification matrix

- No stored preference: existing overview startup/resume sync and configured startup caching remain unchanged.
- Disabled, startup caching off: cold launch and resume show cached categories/feeds without invoking the overview updater.
- Disabled, startup caching previously enabled: neither automatic overview sync nor startup caching begins; the cache option is disabled in settings without losing its stored value.
- Disabled: manual refresh still synchronizes, manual image caching still starts, and opening feeds/articles retains existing fetching behavior.
- Disabled: Tasker/Locale updates remain unaffected.
- Re-enable: future lifecycle updates and configured startup caching work as before; preference changes take effect without stale controller values.
- Offline/invalid configuration and phone/tablet layouts retain existing handling.

## Considerations

- Resuming the category screen and returning from settings can trigger automatic updates today; gate all non-forced category updates rather than only the initial `onCreate` path.
- The setting does not promise zero network traffic: user-driven browsing, manual/background operations, and already-running workers remain outside its scope.
- Runtime backend-request verification requires a device/emulator and a configured backend; do not claim it was performed if unavailable.
