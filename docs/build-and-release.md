# Build & Release

## GitHub Actions

`.github/workflows/build-apk.yml` builds a signed release APK on every push to `main`, every `v*` tag, and on manual dispatch.

The workflow does not use a Gradle wrapper, because the repo has no `gradlew`/`gradle-wrapper.jar`. It installs Gradle 9.3.1 through `gradle/actions/setup-gradle` instead.

### Getting an installable APK

Every push to `main` publishes (and overwrites) the `latest` release, so this URL always serves the newest build:

```
https://github.com/fadheelahmadalfaiz/chroniq-app/releases/download/latest/Chroniq-latest.apk
```

Per-commit builds are also kept as workflow artifacts: **Actions** tab → **Build APK** → pick a run → `chroniq-apk-<sha>`.

## Versioning

`versionCode` and `versionName` in `app/build.gradle.kts` are not touched by CI. During development they stay at `1` / `1.1.0` so the numbers do not creep up.

Android only rejects an install when the new `versionCode` is **lower** than the installed one. The same `versionCode` is accepted and replaces the app in place, so repeated debug builds install fine — as long as the signing key stays the same.

Because the version never changes, builds are told apart by the commit SHA in the release notes.

Before publishing to Google Play, bump `versionCode` (it must increase on every upload) and set a real `versionName`.

## Tagged releases

Tagging publishes the APK as a versioned GitHub Release asset instead of only a rolling build:

```bash
git tag v1.1.0
git push origin v1.1.0
```

## Signing

`app/build.gradle.kts` expects an upload keystore at `$KEYSTORE_PATH` (default `my-upload-key.jks`) with alias `upload`, plus `STORE_PASSWORD` and `KEY_PASSWORD` environment variables.

Without secrets the workflow generates a throwaway keystore per run. That APK installs fine, but it cannot update an install signed by a different key — the user has to uninstall first.

The repository secrets are already configured:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of the `.jks` file (`base64 -w0 my-upload-key.jks`) |
| `KEYSTORE_STORE_PASSWORD` | Keystore password |
| `KEYSTORE_KEY_PASSWORD` | Key password |

The keystore itself lives **outside** the repository, in `chroniq-keys/` next to the project folder, together with `credentials.txt`. Back that folder up. If it is lost, every future build gets a different signature and users have to uninstall the app to update.

Optional: `GEMINI_API_KEY` is written into `.env` at build time so the Secrets Gradle Plugin can package it.

## Local build

`app/build.gradle.kts` reads the keystore from `$KEYSTORE_PATH` and defaults to `my-upload-key.jks` in the project root. Point it at the shared keystore:

```bash
export KEYSTORE_PATH=/path/to/chroniq-keys/chroniq-upload.jks
export STORE_PASSWORD=<store password>
export KEY_PASSWORD=<key password>
gradle :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

A locally built APK is only interchangeable with the CI one when both use the same keystore.

## Known noise in CI logs

KSP2 occasionally prints this on the AWT event thread while shutting down:

```
Exception in thread "AWT-EventQueue-0"
java.lang.NullPointerException: Cannot invoke "...Application.getService(java.lang.Class)" ...
```

It is a shutdown race inside KSP2 when it runs under the in-process Kotlin compiler strategy, set in `gradle.properties`. It does not fail the build — the run still reports success and produces the APK.
