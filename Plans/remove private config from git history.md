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

- Pending final repository, remote and CI checks.
- The project virtual environment created during tooling setup was removed.
  Python tooling for the cleanup runs through `uvx`.
- Rewriting Git refs cannot remove copies in other clones, forks, GitHub cached
  views, pull-request refs, backups or prior CI artifacts. GitHub Support may be
  needed for sensitive-data cache removal. Rotate any exposed credentials.
- Collaborators must resynchronize before pushing; merging old history can
  reintroduce the removed file.
