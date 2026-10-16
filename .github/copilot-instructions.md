# Repository Instructions

## Validation: GitHub Actions only

- Never run local compilation, builds, Gradle tasks, or tests that compile project code, including for validation or troubleshooting. This machine does not have the Android SDK.
- Do not install or configure a local Android SDK to validate changes.
- Make code and test changes locally, but execute compilation and tests exclusively through `.github/workflows/android-build.yml`.
- If CI cannot be run or verified, report validation as blocked. Never substitute a local build or claim success without verifying the workflow run.

## Plan and branch

- For each implementation plan, create a new branch before changing files. Use a descriptive branch name and start from the intended base branch; use `master` unless the task specifies another base.
- Keep the plan current as work proceeds. Save its final, executed version as a Markdown file in `Plans/`, including the work completed, validation results, and any limitations.

## Android builds and artifacts

- Push the task branch to GitHub to trigger the workflow, then confirm the run succeeds. Download its generated APK artifacts into `downloaded-apk/run-{RUN-ID}/`, using the actual GitHub Actions run ID in the directory name.
- Follow the storage layout used for CI run `38658615798`: retain `ttrss-reader-debug.zip` in the run directory and extract it directly into that same directory, preserving the archive's paths. Do not flatten the APK paths or add an extra artifact-name directory:

  ```text
  downloaded-apk/run-{RUN-ID}/
    ttrss-reader-debug.zip
    ttrssreader/build/outputs/apk/
      fdroid/debug/ttrssreader-fdroid-debug.apk
      play/debug/ttrssreader-play-debug.apk
  ```

- Verify that the ZIP and both extracted APKs exist in this layout before reporting the download complete.
- Report clearly if the workflow or artifact download cannot be completed.

## Commit, push, and merge

- Commit all files changed for the task, including the executed plan. Do not include unrelated pre-existing changes, secrets, or generated build files.
- Push the task branch to its GitHub upstream and verify the push succeeds.
- Merge the branch into `master` only after manual tests have passed and the user has explicitly approved the merge. If manual tests have not passed or cannot be run, do not merge.
- As the final interaction, ask the user whether to merge after successful manual tests, or ask for the manual-test result when it is not yet known. Do not ask earlier or merge without approval.

## Backend API

- Find sources for the backend API in https://github.com/tt-rss/tt-rss/
- Refer to the API documentation and code comments within the repository to understand the available endpoints and their usage.
- When implementing features that interact with the backend API, ensure that your requests conform to the expected formats and handle responses appropriately.
- Write tests for any new API interactions to verify correct behavior and error handling.
