# Remove private config from Git history

## Executed plan

1. Inspect tracking, refs, worktrees and published tips without reading private
   configuration values. Obtain approval to rewrite and force-push affected
   published branches.
2. Preserve the locally modified config byte-for-byte outside the repository.
   Create `chore/remove-private-config-history` from `master`.
3. Use `uvx --from git-filter-repo git-filter-repo` to remove the exact root
   `ttrss.config.json` path from every local ref, including agent checkpoints.
   Preserve unaffected public branch tips and ancestry.
4. Keep the local config ignored. Update backend-skill setup documentation to
   copy the committed example without overwriting existing configuration.
5. Verify all-ref history, index, ignore rules, local file integrity and remote
   tips. Force-push only the affected public branch with an exact old-tip lease.
6. Push this task branch, verify Android CI and download its APK artifact.
   Do not run local compilation/tests or merge into `master`.

## Validation and limitations

- PASS: `git log --all -- ttrss.config.json` and
  `git rev-list --objects --all -- ttrss.config.json` return no entries.
  The index does not track the file, ignore checks pass on both affected
  branches, and its local SHA-256 matches the pre-cleanup copy.
- PASS: Git connectivity check. The temporary private backup was removed after
  integrity verification; the original local config remains intact.
- PASS: `feat/ttrss-backend-skill` was force-pushed with an exact old-tip lease;
  its final tip is `1de954897e7ed1b472076bd18f198e9f7fa84a42`.
  Every other previously published branch, including `master`, retained its
  original tip. Setup documentation changes were committed on the feature
  branch; ignore protection and this plan are on the task branch.
- PASS: Android CI
  [38930133368](https://github.com/anno73/ttrss-reader/actions/runs/38930133368)
  on the task branch and
  [38930210612](https://github.com/anno73/ttrss-reader/actions/runs/38930210612)
  on the cleaned feature branch. No local compilation or tests were run.
- PASS: Both runs' ZIP archives were downloaded to
  `downloaded-apk/run-38930133368/` and `downloaded-apk/run-38930210612/`,
  verified against GitHub's SHA-256 digests, and extracted directly into each
  run directory. Each retains `ttrss-reader-debug.zip` and both
  `ttrssreader/build/outputs/apk/{fdroid,play}/debug/` APK paths.
- The final plan-only commit skips CI; executable files are unchanged from the
  successful runs. The user confirmed manual verification passed, including
  continued local backend config operation. The user explicitly approved merging
  `chore/remove-private-config-history` into `master`; the backend-skill branch
  remains separate.
- The project virtual environment created during tooling setup was removed.
  Python tooling for the cleanup runs through `uvx`.
- Rewriting Git refs cannot remove copies in other clones, forks, GitHub cached
  views, pull-request refs, backups or prior CI artifacts. GitHub Support may be
  needed for sensitive-data cache removal. Rotate any exposed credentials.
- Collaborators must resynchronize before pushing; merging old history can
  reintroduce the removed file.
