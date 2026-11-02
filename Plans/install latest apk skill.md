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
4. Prepared the skill and this executed plan for commit/push; verify upstream
   after pushing.
5. Leave manual skill execution and merge approval to the user.

## Validation and limitations

During planning, ADB was found and one authorized phone responded successfully.
Package Manager returned the existing app version and a valid launcher.
These were read-only checks, not an installation test.

Documentation review only: no local builds/tests, GitHub validation, workflow
dispatch or artifact downloads were performed for this change, as requested.
An ordinary push can still trigger the existing workflow automatically.

No APK was installed, no app was launched, and no app data was changed.
The unrelated untracked TODO.md was left untouched. No merge is authorized;
manual skill-test results and explicit approval are required before merging.
