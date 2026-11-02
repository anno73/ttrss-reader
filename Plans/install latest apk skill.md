# Install latest APK skill

## Scope and completed work

Created the instructions-only
[install-latest-apk skill](../.github/skills/install-latest-apk/SKILL.md).
No scripts, tests, application code, dependencies or workflows were changed.

The skill documents:

- PowerShell ADB discovery and authorized phone connectivity checks.
- Explicit source selection: latest across all branches, master, branch or run.
- Successful CI run verification and safe artifact ZIP download/extraction.
- Required ZIP and both APK paths, with only the play APK installed.
- Pre-install and post-install package/version status checks.
- Data-preserving installation, dynamic launcher resolution and bounded
  foreground verification, with explicit failure and partial-success reporting.

## Executed plan

1. Created `feature/install-latest-apk-skill` from upstream master.
2. Added SKILL.md with inline PowerShell/gh/ADB procedures only.
3. Reviewed frontmatter, skill naming, paths, command examples, source semantics,
   native-command error handling, preservation rules and scoped changes.
4. Committed the skill and executed plan in `6697edb0`, pushed the task branch,
   and verified the upstream commit exactly matches the local commit.
   This final execution-record update is delivered in a follow-up commit.
5. The user reported manual skill testing passed and subsequently explicitly
   requested merging the skill branch into master.
6. Update this record, push the skill branch, then merge it into master and
   verify that the pushed master includes both the skill and this plan.

## Validation and limitations

During planning, ADB was found and one authorized phone responded successfully.
Package Manager returned the existing app version and a valid launcher.
These were read-only checks, not an installation test.

Documentation review only: no local builds/tests, GitHub validation, workflow
dispatch or artifact downloads were performed for this change, as requested.
An ordinary push can still trigger the existing workflow automatically.

No APK was installed or launched during skill creation.
The unrelated untracked TODO.md was left untouched.

## Subsequent installation and merge approval

On the user's later "install latest apk" request, the documented procedure was
used to install the play APK from successful workflow run `41535133857`, branch
`fix/render-article-videos`, commit `8e6cc395ee2d6d8f7aefd1aaf14b4b4e7b1d0a60`.
This was a separate installation request, not CI validation of the skill.

- ADB connectivity and the existing installed package were verified.
- The artifact ZIP and both APKs were downloaded and verified in the required
  `downloaded-apk/run-41535133857/` layout.
- Installation used `adb install -r`, preserving app data.
- Version code changed from `215817988` to `215820713`.
- The installed version name exactly matched CI:
  `fix_render-article-videos_2026-11-02_22-11-53`.
- App launch returned `Status: ok`; its resumed foreground activity was verified.
- Manual functional testing of the video change was not reported. This record
  does not authorize merging that separate branch.

The user reported the skill's manual test passed before explicitly requesting
the skill branch merge. Merge scope is only the instructions-only skill and this
executed plan; no application or video-branch changes are included.
