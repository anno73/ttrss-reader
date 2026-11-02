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
- CI only (`.github/workflows/android-build.yml`); no local builds.
- Run `41535133857` on commit `8e6cc395` succeeded:
  - The `ttrss-skill` job and the Android job passed.
  - `compileFdroidDebugUnitTestJavaWithJavac` and `testFdroidDebugUnitTest` ran,
    including `ArticleContentSanitizerTest` and `AutoplayVideosPreferenceTest`.
  - The debug APK was built, and its version, package, provider and signing
    checks passed.
- Artifacts downloaded to `downloaded-apk/run-41535133857/`:
  - `ttrss-reader-debug.zip`
  - `ttrssreader/build/outputs/apk/fdroid/debug/ttrssreader-fdroid-debug.apk`
  - `ttrssreader/build/outputs/apk/play/debug/ttrssreader-play-debug.apk`

## Follow-up: video picture fades out during playback
Manual test of run `41535133857`:
- Videos appeared and sound played.
- During playback the picture faded to the background color within seconds,
  from the top down to the black control bar.
- A paused video showed the player but no image.
- Touching the video brought the player back.

Cause: `ArticleFragment.initUI()` set
`webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)`. This was added in 2013
(commit `7be8b3ee`, "prevent flicker in ics", Android 4.0). A software-layer
WebView cannot composite video frames, which are hardware surfaces, so only the
controls overlay and its gradient were drawn. Audio was unaffected.

Fix: removed the software layer. The WebView now inherits hardware acceleration,
which the manifest already enables. The ICS workaround no longer applies because
`minSdkVersion` is 23. There is no unit test because this is a WebView rendering
change; it is covered by the manual tests below.

## Limitations
- The autoplay setting applies when an article view is created; an open article
  keeps its state until reopened.
- Video playback depends on the device WebView's codec support for the source
  file (article 314961 declares `type="video/mp4"` for its `.mov` URL).

## Manual tests (pending)
- Install the CI APK over the existing installation and confirm settings are kept.
- Article 314961 shows the video and autoplays (muted) by default. The picture
  stays visible while playing and paused, after the controls hide.
- Scrolling, zooming and text rendering in articles show no flicker or artifacts
  with hardware rendering.
- With **Autoplay videos** off, the poster and play button show and the video
  starts only on tap (reopen the article after changing the setting).
- Images still render; cached images and long-press/triple-tap save still work.
