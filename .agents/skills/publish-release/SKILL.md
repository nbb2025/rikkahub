---
name: publish-release
description: Use when preparing or checking a native iAgent Android build for distribution.
---

# iAgent Android builds

Run the tests and verify a device build using `docs/android.md`. Push tested changes to the authorized branch on `nbb2025/rikkahub`. The workflow `.github/workflows/android.yml` runs automatically on `master` and supports a manual run.

For a build requested by the user, verify that the workflow completed and that the APK artifact belongs to the requested commit. Use this repository in GitHub CLI commands, for example `gh run list -R nbb2025/rikkahub --workflow android.yml`.

Follow the user's requested destination when distributing an artifact.
