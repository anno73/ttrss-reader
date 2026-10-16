# Repository Instructions

## Plan and branch

- For each implementation plan, create a new branch before changing files. Use a descriptive branch name and start from the intended base branch; use `master` unless the task specifies another base.
- Keep the plan current as work proceeds. Save its final, executed version as a Markdown file in `Plans/`, including the work completed, validation results, and any limitations.

## Android builds and artifacts

- Do not attempt to build Android APKs locally; this machine does not have the Android SDK. Use the repository's GitHub Actions Android CI workflow to compile and test changes.
- Push the task branch to GitHub to trigger the workflow, then confirm the run succeeds. Download its generated APK artifacts into `downloaded-apk/run-{RUN-ID}/`, using the actual GitHub Actions run ID in the directory name.
- Report clearly if the workflow or artifact download cannot be completed; do not claim a build succeeded without verifying the run.

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
