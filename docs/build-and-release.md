# Build & Release

## GitHub Actions

`.github/workflows/build-apk.yml` builds a signed release APK on every push to `main`, every `v*` tag, and on manual dispatch.

The workflow does not use a Gradle wrapper, because the repo has no `gradlew`/`gradle-wrapper.jar`. It installs Gradle 9.3.1 through `gradle/actions/setup-gradle` instead.

### Getting an installable APK

1. Open the **Actions** tab → **Build APK** → pick a run.
2. Download the `chroniq-apk-<sha>` artifact. It contains `Chroniq-v<version>.apk`.
3. On the phone, allow "Install unknown apps" for the browser/file manager, then open the APK.

Tagging a release publishes the APK as a GitHub Release asset instead of only an artifact:

```bash
git tag v1.1.0
git push origin v1.1.0
```

## Signing

`app/build.gradle.kts` expects an upload keystore at `$KEYSTORE_PATH` (default `my-upload-key.jks`) with alias `upload`, plus `STORE_PASSWORD` and `KEY_PASSWORD` environment variables.

Without secrets the workflow generates a throwaway keystore per run. That APK installs fine, but it cannot update an install signed by a different key — the user has to uninstall first.

For stable, updateable builds add these repository secrets:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of the `.jks` file (`base64 -w0 my-upload-key.jks`) |
| `KEYSTORE_STORE_PASSWORD` | Keystore password |
| `KEYSTORE_KEY_PASSWORD` | Key password |

Optional: `GEMINI_API_KEY` is written into `.env` at build time so the Secrets Gradle Plugin can package it.

## Local build

```bash
keytool -genkeypair -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass android -keypass android -dname "CN=Chroniq"
STORE_PASSWORD=android KEY_PASSWORD=android ./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

## Known noise in CI logs

KSP2 occasionally prints this on the AWT event thread while shutting down:

```
Exception in thread "AWT-EventQueue-0"
java.lang.NullPointerException: Cannot invoke "...Application.getService(java.lang.Class)" ...
```

It is a shutdown race inside KSP2 when it runs under the in-process Kotlin compiler strategy, set in `gradle.properties`. It does not fail the build — the run still reports success and produces the APK.
