---
name: install-latest-apk
description: 'Use when asked to install or update ttrss-reader on an Android phone via ADB, install the latest or master CI APK, check ADB availability or phone connectivity, or verify app status before and after installation.'
---

# Install a CI APK on a phone

This is an instructions-only skill. Use Windows PowerShell 5.1-compatible
commands from the repository root. Do not build locally, run Gradle, or install
an Android SDK. APKs must come from this repository's `android-build.yml`.
Check `$LASTEXITCODE` immediately after every native command; a nonzero exit
means stop and report the failed step, not continue with empty/default data.

## 1. Confirm intent and source

- A check-only request authorizes connectivity and installed-package queries,
  not installation, app launch, workflow dispatch or app-data changes.
- Install the **play** debug APK only unless the user explicitly changes scope.
- Ask for the source unless already specified:
  - **latest**: newest successful workflow run across all branches.
  - **master**: newest successful run on master.
  - Explicit branch or run ID: honor it without substituting another source.
- Pin the selected run ID, branch and commit once. Do not switch targets when
  later runs finish.
- If no successful run exists, a new build may be triggered for the selected
  branch. For an all-branches request with no eligible run, ask which branch to
  build. Never dispatch for a check-only request.

## 2. Check ADB and phone connectivity

```powershell
$adb = Get-Command adb -CommandType Application -ErrorAction SilentlyContinue
if ($null -eq $adb) { throw 'ADB is not available on PATH. Installation is blocked.' }
& $adb.Source version
if ($LASTEXITCODE -ne 0) { throw 'ADB version check failed.' }
& $adb.Source devices -l
if ($LASTEXITCODE -ne 0) { throw 'ADB device enumeration failed.' }
```

Require an authorized device whose state is exactly `device`. For `unauthorized`,
ask the user to unlock the phone and accept its USB-debugging RSA prompt, then
recheck. Report `offline` or no device explicitly. With multiple devices, ask
which serial to use; never guess. Do not commit device identifiers.

Set `$serial` to the selected live serial, not an example or remembered value.
Use `-s $serial` on **every** subsequent device command:

```powershell
& $adb.Source -s $serial get-state
if ($LASTEXITCODE -ne 0) { throw 'Phone connectivity check failed.' }
```

Also require the output to equal `device`. Do not reset the ADB server, unlock
the phone, or change debugging settings automatically.

## 3. Record pre-install package status

The expected package is `org.ttrssreader.anno73`.

```powershell
$package = 'org.ttrssreader.anno73'
$packages = @(& $adb.Source -s $serial shell pm list packages $package)
if ($LASTEXITCODE -ne 0) { throw 'Package Manager lookup failed.' }
$installed = $packages -contains ('package:' + $package)
if ($installed) {
    $status = @(& $adb.Source -s $serial shell dumpsys package $package)
    if ($LASTEXITCODE -ne 0) { throw 'Installed package status query failed.' }
    $status | Select-String '^\s*(versionCode=|versionName=|firstInstallTime=|lastUpdateTime=)'
}
```

Require a valid Package Manager response; service errors or malformed output are
not proof that the app is absent. Record installation presence, version name,
numeric version code, and install/update times when available. For an installed
package, missing version fields block verification. Display only relevant
package fields, not unrelated phone data.

For a check-only request, report this status and stop here. For an installation,
capture a fresh snapshot again if artifact preparation causes a substantial gap.

## 4. Select and verify a successful CI run

Check `Get-Command gh -CommandType Application` and `gh auth status` first.
Never print tokens or read credential stores.

```powershell
gh run list --repo anno73/ttrss-reader --workflow android-build.yml --status success --limit 1 --json databaseId,headBranch,headSha,createdAt,conclusion
```

For a branch source, add `--branch <selected-branch>`. For a run ID, use
`gh run view <run-id> --repo anno73/ttrss-reader` and verify that it belongs to
`android-build.yml`, is completed and successful, and matches the requested
source. Parse JSON only after checking command success.

If dispatch is necessary:

```powershell
gh workflow run android-build.yml --repo anno73/ttrss-reader --ref <selected-branch>
gh run watch <dispatched-run-id> --repo anno73/ttrss-reader --exit-status
```

These are templates: replace placeholders before execution and check each exit
code separately. Correlate the dispatched run using branch, commit, event and
dispatch time. If concurrent runs make identification ambiguous, ask instead of
watching an arbitrary run. Require successful completion before proceeding.

Inspect the pinned run's logs with `gh run view <run-id> --log`. Require the
existing CI verification evidence for the play APK:

- `play APK version verified: <version-name> (<version-code>)`
- Successful expected package/provider identity verification.
- Successful signing identity verification.

Record the exact verified version name/code as the installation target. Do not
derive them from local source or timestamps. Missing logs/evidence block target
verification; do not silently select an older run.

## 5. Download and preserve the artifact ZIP

Use `gh api repos/anno73/ttrss-reader/actions/runs/<run-id>/artifacts` with
pagination if necessary. Select exactly the non-expired artifact named
`ttrss-reader-debug` from that run. Missing/expired/ambiguous artifacts are blocked
states; do not reuse an unrelated cached APK.

Download its ZIP through the authenticated artifact API. `gh run download`
extracts files and does **not** retain the ZIP required by this repository.
For binary output, use `cmd.exe` redirection rather than Windows PowerShell 5.1
`>` or `Out-File`, which can corrupt binary data:

```powershell
# Set these from the verified numeric run/artifact IDs, never arbitrary input.
$runDirectory = Join-Path (Get-Location) ('downloaded-apk\run-' + $runId)
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$zipPath = Join-Path $runDirectory 'ttrss-reader-debug.zip'
$endpoint = 'repos/anno73/ttrss-reader/actions/artifacts/' + $artifactId + '/zip'
& $env:ComSpec /d /c ('gh api "' + $endpoint + '" > "' + $zipPath + '"')
if ($LASTEXITCODE -ne 0) { throw 'Artifact ZIP download failed; do not use partial output.' }
```

Validate IDs as positive decimal integers before interpolating them into the
command. Do not overwrite or trust a partial download on failure. Before
extracting, inspect ZIP entries with .NET `System.IO.Compression.ZipFile`:
reject rooted paths, traversal, or any resolved destination outside the run
directory. Dispose archive handles, then extract directly into the run directory,
preserving paths. Do not flatten or add an artifact-name directory.

Require all three nonempty files with `Test-Path -PathType Leaf` and file lengths:

```text
downloaded-apk\run-{RUN-ID}\
  ttrss-reader-debug.zip
  ttrssreader\build\outputs\apk\
    fdroid\debug\ttrssreader-fdroid-debug.apk
    play\debug\ttrssreader-play-debug.apk
```

Retain the ZIP and both APKs; install only the play APK. Do not add these generated
files to Git. Report download complete only after verifying this exact layout.

## 6. Install, verify and launch

Recheck `get-state` on the selected serial immediately before installation.
Use the verified play APK's absolute path:

```powershell
& $adb.Source -s $serial install -r $playApkPath
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
```

Require the install result to contain `Success` and no failure. Preserve app
data: never automatically uninstall, clear data, add downgrade/signature-bypass
flags, or retry a failed/uncertain installation.

After installation:

1. Recheck connectivity and repeat the package-status queries on the same serial.
2. Require the exact expected package, target version name and numeric version
   code from the pinned run. Report before/after install/update times when
   available; identical versions are valid for a reinstall.
3. Resolve the launcher dynamically:

   ```powershell
   & $adb.Source -s $serial shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $package
   ```

   Check the exit code and require a valid component belonging to the expected
   package. Do not assume a stored activity name is still correct.
4. Launch that component:

   ```powershell
   & $adb.Source -s $serial shell am start -W -n $component
   ```

   Require successful exit and `Status: ok`, with no launch error. An already
   running activity receiving the intent is valid; do not force-stop the app.
5. Inspect `adb -s <serial> shell dumpsys activity activities` for the expected
   package's resumed/foreground activity, checking the exit code. Allow at most
   three checks separated by one second for asynchronous transitions. Missing
   foreground evidence, lock-screen blockers or a failed launch are not success;
   report them separately without automatically unlocking the phone.

An installation can succeed while launch verification fails. Preserve and report
that partial result rather than replaying installation. Foreground verification
is a launch smoke check, not proof of functional correctness.

## Report

Summarize source run/branch/commit, play APK path, download-layout verification,
before/after package presence and version, installation result and launch result.
State blockers and any unverified step explicitly. Do not expose credentials or
unnecessary device identifiers.

Creating or editing this skill does not authorize running it. Do not install or
launch unless requested. Ask for manual functional-test results before proposing
a merge, and never merge without the user's explicit approval.
