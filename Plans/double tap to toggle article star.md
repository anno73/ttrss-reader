# Double-tap to toggle article star

## Problem and approach

Allow a double-tap anywhere in a viewed article's WebView, including on links
and images, to toggle that article's starred state. Extend the existing article
gesture listener in `ArticleFragment` and invoke the same `StarredStateUpdater`
flow as the current star menu action. Keep existing swipe, scroll, and menu
behavior unchanged.

## Todos

1. Add double-tap handling to the article gesture listener, using the existing
   star updater.
2. Validate the change using GitHub Android CI, then inspect the resulting diff.
3. Commit the change and push the current branch to trigger Android CI.
4. Download the Play and F-Droid debug APKs from the successful workflow run.

## Notes

- Article content is rendered by a WebView whose touch events are already
  passed through a `GestureDetector`.
- The article star menu action provides the existing update path to reuse.
- Double taps on the article's interactive content are in scope; taps outside
  the WebView are not.
- The current branch is `master`, tracking `origin/master`.
- The repository's Android CI builds both debug flavors and uploads them as
  `ttrss-reader-debug`; artifact production depends on the CI signing secret
  being configured. If CI skips artifact production, report that limitation
  rather than substituting an unverified artifact.

## Implementation result

- Implemented and pushed as commit `ca350858d244a51ce5f08aa94cb1df0a986200f8`.
- GitHub Android CI run [38655094270](https://github.com/anno73/ttrss-reader/actions/runs/38655094270)
  passed tests, built both APK flavors, and verified their signing identities.
- Both APKs were downloaded to `downloaded-apk/run-38655094270`.
- Double-tap behavior still requires verification on an Android device;
  the CI tests cover timeout diagnostics, not touch gestures.
