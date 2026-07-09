---
name: release
description: Cut a new release of Local Transcribe (android-local-transcribe). Use when asked to "release", "ship a version", "bump the version", "cut a release", or publish new APKs. Bumps the version, tags, and lets CI build + publish the arm64 and universal APKs.
---

# Releasing Local Transcribe

Releases are built and published by CI on tag push. You bump the version and push a tag; GitHub
Actions does the rest.

## Steps

1. **Make sure `main` is green and committed.** All the changes for this version must already be on
   `main` (`git status` clean, pushed).

2. **Bump the version** in `app/build.gradle.kts` (`defaultConfig`):
   - `versionCode` = previous + 1 (monotonic integer),
   - `versionName` = the new semver (e.g. `"1.0.6"`).
   Keep them in sync with the tag you're about to push.

3. **Commit and push** the bump to `main`:
   ```bash
   git add app/build.gradle.kts
   git commit -m "x.y.z: <one-line summary of what's in this release>"
   git push origin main
   ```

4. **Tag and push the tag** (this is what triggers the release):
   ```bash
   git tag vX.Y.Z          # tag name MUST match versionName, prefixed with v
   git push origin vX.Y.Z
   ```

## What happens on tag push

`.github/workflows/release.yml` runs (`ubuntu-latest`):
1. JDK 21 + Android SDK set up.
2. `scripts/fetch-assets.sh` downloads the Parakeet model + Silero VAD + sherpa-onnx AAR
   (cached via `actions/cache`, key `assets-parakeet-v3-sherpa-1.13.3` — only the first run downloads).
3. If the `DEBUG_KEYSTORE_BASE64` repo secret is set, it's decoded to a temp file and pinned via the
   `DEBUG_KEYSTORE_PATH` env var (read by the `debug` signingConfig in `app/build.gradle.kts`), so the
   APKs are signed with the **same key as every prior release** (installs as an in-place update).
   Locally (no `DEBUG_KEYSTORE_PATH`) the build falls back to `~/.android/debug.keystore` — the same
   key the secret was created from. The keystore is never committed.
4. `./gradlew :app:assembleRelease` builds the per-ABI APKs.
5. A GitHub release named `Local Transcribe vX.Y.Z` is published with auto-generated notes and two
   assets attached:
   - `android-local-transcribe-vX.Y.Z-arm64.apk` (arm64-v8a — recommended for phones)
   - `android-local-transcribe-vX.Y.Z.apk` (universal)
   - `android-local-transcribe-vX.Y.Z-arm64-nnapi.apk` (arm64, built with `-PasrProvider=nnapi` —
     experimental NNAPI/accelerator offload; falls back to CPU if no accelerator)

## Verify

```bash
gh run watch --repo mtib/android-local-transcribe                 # follow the build
gh release view vX.Y.Z --repo mtib/android-local-transcribe --json assets --jq '.assets[].name'
```

The release URL is `https://github.com/mtib/android-local-transcribe/releases/tag/vX.Y.Z`.

## Manual trigger (no new tag)

To rebuild an existing tag's release, use the workflow's `workflow_dispatch`:
```bash
gh workflow run release.yml --repo mtib/android-local-transcribe -f tag=vX.Y.Z
```

## Notes / gotchas

- **Tag must equal versionName** (prefixed `v`). Asset filenames use the tag.
- **Signing:** keep the `DEBUG_KEYSTORE_BASE64` secret set (base64 of the debug keystore that signed
  earlier releases). If it's ever lost, new releases get a different signature and users must
  uninstall before updating.
- **Never add a network dependency / the `INTERNET` permission** — the offline guarantee is a
  product requirement, and CI/QA assert it's absent from the merged manifest.
- Releases are large (~675 MB arm64 / ~715 MB universal) because the model is bundled; GitHub's 2 GB
  per-asset limit covers this.
