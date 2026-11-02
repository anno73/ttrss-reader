# Show article videos with configurable autoplay

## Problem
Images in articles rendered, but HTML5 videos did not. Example: tt-rss article
314961 (feed 319) contains only:

```html
<figure><video controls autoplay playsinline muted poster="https://…jpg">
  <source src="https://…mov" type="video/mp4"></source></video></figure>
```

The backend content was correct. The app removed the video before rendering.

## Root cause
- `ArticleFragment.doRefresh()` sanitized content with
  `Jsoup.clean(article.content, Safelist.relaxed())`. The relaxed safelist allows
  `img` but not `figure`, `video` or `source`, so the whole video was removed
  (no text remained).
- The WebView never enabled autoplay for normal use: the old code called
  `setMediaPlaybackRequiresUserGesture(false)` only when media loading was
  disabled (an inverted condition).
- The protocol-relative URL rewrite (`//host` → `https://host`) in
  `JSONConnector` covered `img`/`video` `src`, but not `<source src>`.

## Decisions (confirmed)
- New setting **Autoplay videos**, default **on**. When off, the `autoplay`
  attribute is stripped and playback needs a tap on the play button. Controls
  are always kept.
- Videos stream from their origin; they are not added to the image cache.
- App updates must not break: no migrations, renamed keys, schema or signing changes.

## Work completed
1. `utils/ArticleContentSanitizer`:
   - `clean(html, allowAutoplay)` extends `Safelist.relaxed()` with `figure`,
     `figcaption`, `video` (`src`, `poster`, `controls`, `loop`, `muted`,
     `playsinline`, `preload`, `width`, `height`, and `autoplay` only when allowed)
     and `source` (`src`, `type`).
   - Media URL attributes accept `http`/`https` only.
   - `normalizeProtocolRelativeUrls(html)` handles `img`, `video` and `source`
     and is now used by `JSONConnector`.
2. `ArticleFragment.doRefresh()` uses the sanitizer and sets
   `setMediaPlaybackRequiresUserGesture(!autoplayVideos)`.
3. Preference:
   - `Constants.AUTOPLAY_VIDEOS = "AutoplayVideosPreference"` and
     `AUTOPLAY_VIDEOS_DEFAULT = true`. The `_DEFAULT` naming keeps
     `resetPreferences()` working.
   - `Controller.autoplayVideos()` reads the preference directly (no cached field).
   - `prefs_usage.xml` adds a checkbox after **Load media**, which it depends on.
   - Strings added in English and German.
4. Update safety: an absent key on upgraded installs falls back to `true`. No
   existing keys, DB schema, `applicationId` or signing configuration changed.
   The sanitizer is a strict superset of the previous safelist, so image
   rendering, cached-image JS rewriting, alt-text lookup and media saving are
   unchanged. The article CSS already limits `video` to `max-width: 100%`.
5. Tests:
   - `ArticleContentSanitizerTest` covers the article 314961 markup, autoplay
     kept or stripped, controls kept, images unchanged, `figcaption`, and removal
     of scripts, event handlers, `javascript:`/`data:` URLs and iframes. It also
     covers protocol-relative rewriting.
   - `AutoplayVideosPreferenceTest` covers the default, the upgrade fallback,
     live reads and reset.
   - Both are added to the CI unit-test step.
6. Docs: README "Article videos" section and feature list; CHANGELOG entry.

## Validation
CI only (`.github/workflows/android-build.yml`); no local builds. Results are
recorded below once the run completes.

## Manual tests (pending)
- Install the CI APK over the existing installation and confirm settings are kept.
- Article 314961 shows the video and autoplays (muted) by default.
- With **Autoplay videos** off, the poster and play button show and the video
  starts only on tap (reopen the article after changing the setting).
- Images still render; cached images and long-press/triple-tap save still work.
