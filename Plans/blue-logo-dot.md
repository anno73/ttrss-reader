# Blue logo dot

## Goal and approved approach

Recolor the bottom-left dot blue in the app icon while leaving the central t
and orange waves unchanged in the highest-resolution source. Preserve the dot's
shading, saturation, lightness, and transparency.

The user explicitly approved rendering the entire edited 96 px icon down to
72, 48, and 36 px, accepting resampling changes to other artwork at smaller sizes.
Only the four `drawable-*dpi/icon.png` resources are in scope; metadata artwork
is unchanged.

## Work completed

1. Created `artwork/blue-logo-dot` from the fetched `origin/master` at
   `cbe4614df05e60af1e27534ca827678d44644100`. Preserved the unrelated untracked
   `TODO.md`.
2. Used existing WSL Pillow; no tools were installed.
3. Identified the isolated opaque/partially transparent dot component in the
   96 x 96 source, with bounds `(0, 68)` through `(27, 95)`.
4. Rotated the hue of its 618 pixels by 180 degrees, preserving HSL saturation
   and lightness exactly. The representative center changed from `#FA9D39`
   to `#3996FA`, a complementary blue with matching depth and saturation.
5. Generated each smaller image directly from the edited 96 px source with
   Lanczos downsampling in premultiplied-alpha space to avoid dark edge fringes.
6. Visually inspected all four resulting images.

## Image validation

- PNG readability and exact expected dimensions verified: xhdpi 96 x 96,
  hdpi 72 x 72, mdpi 48 x 48, ldpi 36 x 36.
- PNG round-trip pixel equality verified for all four outputs.
- Source alpha channel is byte-identical to the original.
- Every source pixel outside the dot is byte-identical to the original,
  including the central t and both orange waves.
- Dot saturation and lightness are unchanged for every edited pixel.
- Image-processing scripts and original comparison image are session-local,
  not repository files.
- No local project compilation, Gradle tasks, or project tests were run.

## CI, distribution, and remaining work

Commit and push the task files, verify the Android CI workflow, then retain
the downloaded ZIP and extract both APKs into:

```text
downloaded-apk/run-{RUN-ID}/
  ttrss-reader-debug.zip
  ttrssreader/build/outputs/apk/
    fdroid/debug/ttrssreader-fdroid-debug.apk
    play/debug/ttrssreader-play-debug.apk
```

CI run and artifact verification are pending the initial task push. Record the
actual result here after verification. Generated artifacts must not be committed.

Manual device tests are pending. Do not merge until those tests pass and the
user explicitly approves the merge.
