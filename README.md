<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/bc0ec4ff-f3af-4ae2-8044-3cba28850995

## Download the app

[![Build APK](https://github.com/fadheelahmadalfaiz/chroniq-app/actions/workflows/build-apk.yml/badge.svg)](https://github.com/fadheelahmadalfaiz/chroniq-app/actions/workflows/build-apk.yml)

During development the version number stays fixed. Every push to `main` overwrites the same release, so this link always serves the newest build:

**https://github.com/fadheelahmadalfaiz/chroniq-app/releases/download/latest/Chroniq-latest.apk**

Tagged releases are kept separately: [all releases](https://github.com/fadheelahmadalfaiz/chroniq-app/releases).

Install steps:

1. Download the APK on your phone.
2. Allow "Install unknown apps" for your browser or file manager when prompted.
3. Open the APK and confirm the install. It installs over the previous build, so alarms stay intact.

Android 7.0 (API 24) or newer is required. See [docs/build-and-release.md](docs/build-and-release.md) for how the APK is built and signed.

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.
