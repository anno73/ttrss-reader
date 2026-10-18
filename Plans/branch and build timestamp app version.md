# Branch and build timestamp app version

## Agreed scope

- Replace `1.98.2` with `<normalized-branch>_yyyy-MM-dd_HH-mm-ss`, in UTC.
- Replace each branch character outside `[A-Za-z0-9._-]` with `_`; use the PR
  source branch, never its temporary merge ref. Fail on unidentified branches.
- Derive numeric version code from the same instant as whole seconds since
  `2020-01-01T00:00:00Z`. Require `1982 < code <= 2100000000`.
- Show the installed version in a read-only Version row under Preferences >
  Information. Reuse package metadata so it agrees with the existing About screen.
- Preserve application ID, signing, flavors and existing new-version handling.
- No backend API changes. No local compilation, Gradle execution or Android SDK.

## Executed work

1. Created `feature/branch-build-version` from `origin/master` (`d2102f62`).
   Left the unrelated media-folder branch and untracked TODO file untouched.
2. Added [shared Gradle version generation](../gradle/app-version.gradle),
   wired it into the [module configuration](../ttrssreader/build.gradle), and
   removed obsolete manifest version attributes.
3. Added the read-only preference and default title resource. Its summary uses
   the existing installed-package version helper; it has no click action or
   persistent preference value.
4. Updated [Android CI](../.github/workflows/android-build.yml) to capture one
   UTC instant for all Gradle invocations and both APK flavors, test the actual
   generator, and compare both packaged version names and codes with independently
   calculated expectations. Existing unit tests and identity/signing checks remain.
5. Documented format, inputs, UTC, code limits and update behavior in the
   [README](../README.md).
6. Remaining execution: commit/push, verify CI, download and verify artifacts,
   record results here, and request manual testing. No merge until manual tests
   pass and the user explicitly approves it.

## Validation

- Editor diagnostics: no reported errors in the changed Java, Gradle or workflow
  files. This is not a substitute for CI compilation.
- Generator checks in CI cover the requested example formats, punctuation,
  non-ASCII and long branch names, epoch conversion, the maximum accepted code,
  missing branches, malformed dates, fractional/non-UTC timestamps, leap seconds,
  and lower/upper range rejection.
- CI run and artifact verification: pending.
- Manual Android tests: pending.

## Manual acceptance

- Install the matching-signer APK over an existing installation; settings/data
  must remain intact.
- Preferences > Information > Version must show the complete branch/time string,
  wrap long names, and be read-only.
- Compare with About and Android's installed-app version information.
- Verify both flavors share the name and code; numeric code must match the UTC
  build instant's seconds since 2020-01-01.
- Check existing one-time new-version behavior and that reopening the same
  installed build does not treat it as newly installed again.

## Limitations and decisions

- `yyyyMMddHHmmss` cannot fit Android's standard integer version code. The chosen
  epoch-offset scheme retains seconds and reaches the Play limit in 2086.
- Normal updates reject a lower code, even across branches. Rebuild the desired
  branch later to obtain a higher code with the same signing key.
- Codes follow build time, not commit ancestry or completion order, and depend
  on an accurate clock. Same-second builds can share codes; this does not
  guarantee unique Play release codes.
- The 2000 timestamp examples are tested for version-name formatting only:
  actual builds that early are rejected by the numeric-code lower bound.
- New title translations use Android's default-resource fallback.
