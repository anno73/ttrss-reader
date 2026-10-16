# Remove former-maintainer funding references

## Objective

Remove donation requests and sponsor links belonging to the previous maintainer from this fork, while retaining project lineage credits and licensing information.

## Changes

- Removed the README Donations section and its donation-only PayPal/image/wiki references.
- Removed `.github/FUNDING.yml`, which directed GitHub Sponsors and custom PayPal funding to the previous maintainer.
- Kept the README's credit to the original Google Code `ttrss-reader` and intermediate `nilsbraden/ttrss-reader-fork`.
- Kept `ttrssreader/LICENSE` unchanged.

## Validation

- Pending: review the focused diff and confirm only funding links were removed.
- Pending: push the branch to trigger `.github/workflows/android-build.yml`; confirm workflow success and retrieve/verify its APK artifact.
- No local Android compilation or tests will be run, in accordance with repository validation requirements.
- No merge will be performed without successful manual tests and explicit user approval.
