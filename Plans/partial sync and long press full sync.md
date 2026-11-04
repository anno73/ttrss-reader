# Partial sync on single tap, full sync on long press

Branch: `feature/partial-sync-long-press-full-sync` (from `master`)

## Problem
The top-bar **Refresh** icon always runs a full sync, which is slow for large feed sets.
Users want a fast partial sync of recent articles by default, and a full sync on demand.

## Decisions
- A single tap always runs a partial sync (articles from the last N days or weeks only).
- A long press runs the existing full sync after a short vibration (same `Utils.alert` feedback as the list-end
  navigation). A long press is attached to the action-bar item only; overflow items do not get a long press. This is
  accepted.
- Virtual categories and feeds (negative IDs) always use the full sync. On the category list, the single tap uses
  `VCAT_ALL` (all articles) for the partial fetch, and labels, virtual categories and purge are skipped.
- Already fetched articles are kept. The animated refresh icon runs during both syncs (`ActivityUpdater`).
- Default window: 7 days. Unit: days or weeks, configurable under **Settings > Connection**.

## API findings
- tt-rss `getHeadlines` has no date-range parameter (wiki: `since_id`, `view_mode`, `limit`, `skip`, `order_by`).
- Partial sync sends `order_by=feed_dates` (newest first by `updated`) and stops paging once a batch's oldest
  `updated` is older than the cutoff. Articles older than the cutoff are not inserted.
- The `order_by=feed_dates` behaviour is taken from the wiki and must be confirmed against a live server (manual
  test). If the server ignores it, the early stop is unsafe.

## Side-effect guards
- The partial path does not touch update throttle timestamps (`articlesChanged`/`feedsChanged`), so a later
  automatic sync is not suppressed.
- The partial path does not call `setSinceId` (valid for full caching only).
- The partial path does not run `handlePurgeMarked` (starred/published unmark logic).
- The partial path does not run `purgeOrphanedArticles`.
- Partial fetches with a null filter, so `updated` is always present for the stop check.

## Work completed
- `Constants.java`: keys and defaults (`PARTIAL_SYNC_AMOUNT` 7, `PARTIAL_SYNC_UNIT` "days").
- `Controller.java`: `partialSyncAmount()`, `partialSyncUnit()`, `partialSyncCutoffMillis()` (SSID-aware).
- `prefs_connection.xml`, `arrays.xml`, `strings.xml`: amount (integer, validated as positive) and unit list.
- `ConnectionPreferencesFragment.java`: validation of the amount.
- `JSONConnector.java`: `order_by` constant, `parseArticleArray` cutoff and oldest tracking, `getHeadlinesSince`.
  `getHeadlines` public signature unchanged.
- `Data.java`: `updateArticlesPartial` (no throttle, since-ID or purge side effects).
- `MenuActivity.java`: `doPartialUpdate()` default (calls `doUpdate(true)`), long-press listener on the refresh
  `ActionMenuItemView` (vibrate, then full update), attached on each `onPrepareOptionsMenu`.
- `CategoryActivity.java`: `CategoryPartialUpdater` (4 tasks), single tap uses it, guards as before.
- `FeedHeadlineActivity.java`: `FeedHeadlinePartialUpdater`; negative target IDs fall back to the full update.
- `README.md`: "Manual refresh" section.

## Validation
- Local Gradle build and tests are not run (repository rule: CI only).
- CI: `.github/workflows/android-build.yml` on the task branch, run `41890167644` (commit `2d540314`): success.
  Artifact `ttrss-reader-debug` downloaded to `downloaded-apk/run-41890167644/` (ZIP plus
  `ttrssreader/build/outputs/apk/{fdroid,play}/debug/*.apk`).
- Manual tests (not yet run; merge waits for the user's approval): partial window with 7 days and 2 weeks; old
  article stop; `order_by=feed_dates` on a live server; long-press vibration; animation during both syncs; virtual
  feed full sync; offline and SSID-specific settings.

## Limitations
- Overflow menu: no long press (accepted).
- Partial sync is capped by the page limit; very busy feeds may not fetch the full window in one tap.
- German and other locale strings for the new preferences are not translated yet (English fallback).
- `order_by=feed_dates` is unverified against a live server.
