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

## CI and distribution

- Committed the icons and this plan as
  `cf78f82acc9b8a01b4e0b592581a5fb7d885fe7a`, pushed the task branch, and
  verified that the remote commit matched.
- [Android CI run 41522544589](https://github.com/anno73/ttrss-reader/actions/runs/41522544589)
  succeeded for that exact commit. Both jobs passed, including unit tests,
  both debug APK builds, and package/version/provider/signing verification.
- Downloaded the original `ttrss-reader-debug.zip` and extracted it directly
  into the run directory, preserving archive paths. Verified that the ZIP and
  both APKs exist:

```text
downloaded-apk/run-41522544589/
  ttrss-reader-debug.zip
  ttrssreader/build/outputs/apk/
    fdroid/debug/ttrssreader-fdroid-debug.apk
    play/debug/ttrssreader-play-debug.apk
```

- Opened both APK archives and verified that all four packaged icon densities
  match the edited source pixels exactly after RGBA decoding.
- Confirmed downloaded artifacts are ignored by Git; they are not committed.
- Recorded this final validation in a documentation-only follow-up commit with
  `[skip ci]`; no app files changed after the successful CI run.

## Limitations and manual testing

- CI reported an existing non-blocking Node.js 20 deprecation warning for
  `actions/checkout@v4`; the workflow succeeded.
- Manual device tests are pending. Install the appropriate APK, check the
  launcher and in-app icon for the blue dot, unchanged t and orange waves, and
  clean edges at the device's density.
- No merge was performed. Ask for the manual-test result; merge only after
  passing tests and explicit user approval.
